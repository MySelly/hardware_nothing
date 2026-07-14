/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.DolbyConstants.DsParam
import org.lunaris.dolby.R
import org.lunaris.dolby.audio.DolbyAudioEffect
import org.lunaris.dolby.domain.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class DolbyRepository(private val context: Context) : AutoCloseable {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val dolbyEffect: DolbyAudioEffect
        get() = getOrCreateDolbyEffect()
    
    private val defaultPrefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
    private val presetsPrefs = context.getSharedPreferences(DolbyConstants.PREF_FILE_PRESETS, Context.MODE_PRIVATE)
    private val customPresetsPrefs = context.getSharedPreferences(
        DolbyConstants.PREF_FILE_CUSTOM_PRESETS,
        Context.MODE_PRIVATE
    )
    
    private val deviceStateManager = DeviceStateManager(context)
    private val audioEnginePrefs = AudioEnginePreferences(context)

    private fun clampGeqGain(gain: Int): Int = audioEnginePrefs.clampEqGain(gain)

    private val audioTuning = AudioTuningController(
        context = context,
        dolbyEffectProvider = { dolbyEffect },
        checkEffect = { checkEffect() },
        isReleased = { isReleased },
        profilePrefs = { getProfilePrefs(it) },
        defaultPrefs = defaultPrefs,
        activeDeviceCategory = { resolveActiveAudioDevice().category }
    )

    private val _activeAudioDevice = MutableStateFlow(resolveActiveAudioDevice())
    val activeAudioDevice: StateFlow<ActiveAudioDevice> = _activeAudioDevice.asStateFlow()

    private val _isOnSpeaker = MutableStateFlow(_activeAudioDevice.value.isOnSpeaker)
    val isOnSpeaker: StateFlow<Boolean> = _isOnSpeaker.asStateFlow()
    
    private val _currentProfile = MutableStateFlow(0)
    val currentProfile: StateFlow<Int> = _currentProfile.asStateFlow()

    val stereoWideningSupported = context.resources.getBoolean(R.bool.dolby_stereo_widening_supported)
    val volumeLevelerSupported = context.resources.getBoolean(R.bool.dolby_volume_leveler_supported)
    
    private var isReleased = false
    
    private var cachedPresets: List<EqualizerPreset>? = null
    private val presetCacheLock = Any()
    private var isBypassActive = false

    private fun checkEffect() {
        if (isReleased) {
            DolbyConstants.dlog(TAG, "Repository released, skipping effect check")
            return
        }

        var recovered = false
        try {
            synchronized(EFFECT_LOCK) {
                val effect = getOrCreateDolbyEffect()
                if (!effect.hasControl()) {
                    DolbyConstants.dlog(TAG, "Shared audio effect lost control, recreating once")
                    effect.release()
                    sharedDolbyEffect = null
                    getOrCreateDolbyEffect()
                    recovered = true
                }
            }
            if (recovered) restoreSavedProfileIfNeeded()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error checking effect: ${e.message}")
        }
    }

    fun hasEffectControl(): Boolean {
        if (isReleased) return false
        return try {
            synchronized(EFFECT_LOCK) { getOrCreateDolbyEffect().hasControl() }
        } catch (_: Exception) {
            false
        }
    }

    private fun readSavedProfile(): Int? {
        return defaultPrefs.getString(DolbyConstants.PREF_PROFILE, null)
            ?.toIntOrNull()
    }

    private fun restoreSavedProfileIfNeeded() {
        val savedProfile = readSavedProfile() ?: return
        if (dolbyEffect.profile != savedProfile) {
            dolbyEffect.profile = savedProfile
        }
        restoreProfilePreset(savedProfile)
        applyProfileSettings(savedProfile)
    }

    private fun applyProfileSettings(profile: Int) {
        try {
            val prefs = getProfilePrefs(profile)
            
            val ieqPreset = prefs.getString(DolbyConstants.PREF_IEQ, "0")?.toIntOrNull() ?: 0
            dolbyEffect.setDapParameter(DsParam.IEQ_PRESET, ieqPreset, profile)
            
            val hpVirtualizer = prefs.getBoolean(DolbyConstants.PREF_HP_VIRTUALIZER, false)
            dolbyEffect.setDapParameter(DsParam.HEADPHONE_VIRTUALIZER, hpVirtualizer, profile)
            
            val spkVirtualizer = prefs.getBoolean(DolbyConstants.PREF_SPK_VIRTUALIZER, false)
            dolbyEffect.setDapParameter(DsParam.SPEAKER_VIRTUALIZER, spkVirtualizer, profile)
            
            if (stereoWideningSupported) {
                val stereoWidening = prefs.getInt(DolbyConstants.PREF_STEREO_WIDENING, 32)
                dolbyEffect.setDapParameter(DsParam.STEREO_WIDENING_AMOUNT, stereoWidening, profile)
            }
            
            val dialogueEnabled = prefs.getBoolean(DolbyConstants.PREF_DIALOGUE, false)
            dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_ENABLE, dialogueEnabled, profile)
            
            val dialogueAmount = prefs.getInt(DolbyConstants.PREF_DIALOGUE_AMOUNT, 6)
            dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_AMOUNT, dialogueAmount, profile)
            
            val bassEnabled = prefs.getBoolean(DolbyConstants.PREF_BASS, false)
            dolbyEffect.setDapParameter(DsParam.BASS_ENHANCER_ENABLE, bassEnabled, profile)
            
            if (volumeLevelerSupported) {
                val volumeLeveler = prefs.getBoolean(DolbyConstants.PREF_VOLUME, false)
                dolbyEffect.setDapParameter(DsParam.VOLUME_LEVELER_ENABLE, volumeLeveler, profile)
            }

            audioTuning.applyAll(profile)
            
            DolbyConstants.dlog(TAG, "Successfully restored all settings for profile $profile")
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Failed to restore profile settings: ${e.message}")
        }
    }

    fun applySavedState() {
    checkEffect()
        val enabled = defaultPrefs.getBoolean(DolbyConstants.PREF_ENABLE, false)
        dolbyEffect.dsOn = enabled
        if (enabled) {
            restoreSavedProfileIfNeeded()
        }
    }

    fun getCurrentOutputDevice(): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (type in OUTPUT_DEVICE_PRIORITY) {
            val device = devices.firstOrNull { it.type == type }
            if (device != null) return device
        }
        return devices.firstOrNull()
    }

    private fun resolveActiveAudioDevice(): ActiveAudioDevice {
        val device = getCurrentOutputDevice() ?: return ActiveAudioDevice.Unknown
        return ActiveAudioDevice(
            name = deviceStateManager.deviceDisplayName(device),
            category = device.toAudioCategory()
        )
    }

    private fun AudioDeviceInfo.toAudioCategory(): AudioDeviceCategory {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioDeviceCategory.SPEAKER
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioDeviceCategory.WIRED
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> AudioDeviceCategory.BLUETOOTH
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE -> AudioDeviceCategory.USB
            else -> AudioDeviceCategory.OTHER
        }
    }

    fun updateSpeakerState() {
        if (!isReleased) {
            val activeDevice = resolveActiveAudioDevice()
            _activeAudioDevice.value = activeDevice
            _isOnSpeaker.value = activeDevice.isOnSpeaker
        }
    }

    fun getDolbyEnabled(): Boolean {
        return try {
            dolbyEffect.dsOn
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting Dolby enabled state: ${e.message}")
            false
        }
    }

    fun setDolbyEnabled(enabled: Boolean) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.dsOn = enabled
            defaultPrefs.edit().putBoolean(DolbyConstants.PREF_ENABLE, enabled).apply()
            isBypassActive = false
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting Dolby enabled: ${e.message}")
        }
    }

    fun setDolbyBypass(bypass: Boolean) {
        if (isReleased) return

        try {
            checkEffect()
            if (bypass) {
                if (!isBypassActive && defaultPrefs.getBoolean(DolbyConstants.PREF_ENABLE, false)) {
                    dolbyEffect.dsOn = false
                    isBypassActive = true
                    DolbyConstants.dlog(TAG, "Dolby bypass active")
                }
            } else if (isBypassActive) {
                val savedEnabled = defaultPrefs.getBoolean(DolbyConstants.PREF_ENABLE, false)
                dolbyEffect.dsOn = savedEnabled
                isBypassActive = false
                DolbyConstants.dlog(TAG, "Dolby bypass released")
            }
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting Dolby bypass: ${e.message}")
        }
    }

    fun getCurrentProfile(): Int {
        return try {
            checkEffect()
            restoreSavedProfileIfNeeded()
            dolbyEffect.profile
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting current profile: ${e.message}")
            0
        }
    }

    fun setCurrentProfile(profile: Int) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.profile = profile
            defaultPrefs.edit().putString(DolbyConstants.PREF_PROFILE, profile.toString()).apply()
            if (!verifyProfileSaved(profile)) {
                DolbyConstants.dlog(TAG, "WARNING: Profile may not have been saved correctly!")
            }
            restoreProfilePreset(profile)
            applyProfileSettings(profile)
            _currentProfile.value = profile
            DolbyConstants.dlog(TAG, "Profile set to: $profile")
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting current profile: ${e.message}")
        }
    }

    fun cycleToNextProfile(): Int {
        val profileIds = context.resources.getStringArray(R.array.dolby_profile_values)
            .map { it.toInt() }
        if (profileIds.isEmpty()) return getCurrentProfile()

        val current = getCurrentProfile()
        val currentIndex = profileIds.indexOf(current)
        val nextProfile = profileIds[(currentIndex + 1) % profileIds.size]
        setCurrentProfile(nextProfile)
        return nextProfile
    }

    fun getProfileDisplayName(profile: Int): String {
        val profiles = context.resources.getStringArray(R.array.dolby_profile_entries)
        val profileValues = context.resources.getStringArray(R.array.dolby_profile_values)
        val index = profileValues.indexOf(profile.toString())
        return if (index != -1) profiles[index] else context.getString(R.string.dolby_unknown)
    }

    private fun restoreProfilePreset(profile: Int) {
        audioTuning.pushGeqToHardware(profile)
    }

    fun verifyProfileSaved(profile: Int): Boolean {
        val prefs = defaultPrefs.getString(DolbyConstants.PREF_PROFILE, "0")?.toIntOrNull()
        val saved = prefs == profile
        DolbyConstants.dlog(TAG, "Profile verification: requested=$profile, saved=$prefs, match=$saved")
        return saved
    }

    private fun getProfilePrefs(profile: Int): SharedPreferences {
        return context.getSharedPreferences("profile_$profile", Context.MODE_PRIVATE)
    }

    fun getBandMode(): BandMode {
        val mode = defaultPrefs.getString(DolbyConstants.PREF_BAND_MODE, "10")
        return when (mode) {
            "10" -> BandMode.TEN_BAND
            "15" -> BandMode.FIFTEEN_BAND
            "20" -> BandMode.TWENTY_BAND
            else -> BandMode.TEN_BAND
        }
    }

    fun setBandMode(mode: BandMode) {
        defaultPrefs.edit().putString(DolbyConstants.PREF_BAND_MODE, mode.value).apply()
    }

    fun getBassEnhancerEnabled(profile: Int): Boolean {
        return try {
            dolbyEffect.getDapParameterBool(DsParam.BASS_ENHANCER_ENABLE, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting bass enhancer: ${e.message}")
            false
        }
    }

    fun setBassEnhancerEnabled(profile: Int, enabled: Boolean) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.BASS_ENHANCER_ENABLE, enabled, profile)
            getProfilePrefs(profile).edit().putBoolean(DolbyConstants.PREF_BASS, enabled).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting bass enhancer: ${e.message}")
        }
    }

    fun getBassLevel(profile: Int): Int {
        val prefs = getProfilePrefs(profile)
        return prefs.getInt(DolbyConstants.PREF_BASS_LEVEL, 0)
    }

    fun getBassCurve(profile: Int): Int {
        val prefs = getProfilePrefs(profile)
        return prefs.getInt(DolbyConstants.PREF_BASS_CURVE, 0)
    }

    fun setBassCurve(profile: Int, curve: Int) {
        if (isReleased) return
        
        try {
            val prefs = getProfilePrefs(profile)
            val previousCurve = prefs.getInt(DolbyConstants.PREF_BASS_CURVE, 0)
            val level = prefs.getInt(DolbyConstants.PREF_BASS_LEVEL, 0)
            if (previousCurve == curve) return

            prefs.edit().putInt(DolbyConstants.PREF_BASS_CURVE, curve).apply()

            if (level <= 0) return
            checkEffect()
            val currentGains = audioTuning.getRawGeqArray(profile)?.copyOf()
                ?: dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
            val modifiedGains = currentGains.copyOf()
            applyBassCurve(modifiedGains, level, previousCurve, -1)
            applyBassCurve(modifiedGains, level, curve, 1)
            audioTuning.saveRawGeqArray(profile, modifiedGains)
            audioTuning.pushGeqToHardware(profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting bass curve: ${e.message}")
            throw e
        }
    }

    private fun applyBassCurve(gains: IntArray, level: Int, curve: Int, direction: Int) {
        val weights = BASS_CURVES.getOrElse(curve) { BASS_CURVES[0] }
        val baseGain = level * BASS_GAIN_MULTIPLIER
        for (i in weights.indices) {
            if (i >= gains.size) break
            val weightedGain = (baseGain * weights[i] * direction).toInt()
            gains[i] = clampGeqGain(gains[i] + weightedGain)
        }
    }

    fun setBassLevel(profile: Int, level: Int) {
        if (isReleased) return
        
        DolbyConstants.dlog(TAG, "setBassLevel: profile=$profile level=$level")

        if (level !in 0..100) {
            DolbyConstants.dlog(TAG, "setBassLevel: invalid level $level")
            throw IllegalArgumentException("Bass level must be between 0 and 100")
        }
        
        try {
            val prefs = getProfilePrefs(profile)
            val previousLevel = prefs.getInt(DolbyConstants.PREF_BASS_LEVEL, 0)
            
            prefs.edit().putInt(DolbyConstants.PREF_BASS_LEVEL, level).apply()
            
            setBassEnhancerEnabled(profile, level > 0)
            
            checkEffect()
            val currentGains = audioTuning.getRawGeqArray(profile)?.copyOf()
                ?: dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
            val modifiedGains = currentGains.copyOf()
            
            val curve = prefs.getInt(DolbyConstants.PREF_BASS_CURVE, 0)
            if (previousLevel > 0) {
                applyBassCurve(modifiedGains, previousLevel, curve, -1)
            }

            if (level > 0) {
                applyBassCurve(modifiedGains, level, curve, 1)
            }
            audioTuning.saveRawGeqArray(profile, modifiedGains)
            audioTuning.pushGeqToHardware(profile)
            
            DolbyConstants.dlog(TAG, "setBassLevel: success")
        } catch (e: IllegalArgumentException) {
            DolbyConstants.dlog(TAG, "setBassLevel: validation error - ${e.message}")
            val prefs = getProfilePrefs(profile)
            prefs.edit().putInt(DolbyConstants.PREF_BASS_LEVEL, 0).apply()
            throw e
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "setBassLevel: unexpected error - ${e.message}")
            throw e
        }
    }

    fun getTrebleEnhancerEnabled(profile: Int): Boolean {
        val prefs = getProfilePrefs(profile)
        return prefs.getBoolean(DolbyConstants.PREF_TREBLE, false)
    }

    fun setTrebleEnhancerEnabled(profile: Int, enabled: Boolean) {
        getProfilePrefs(profile).edit().putBoolean(DolbyConstants.PREF_TREBLE, enabled).apply()
    }

    fun getTrebleLevel(profile: Int): Int {
        val prefs = getProfilePrefs(profile)
        return prefs.getInt(DolbyConstants.PREF_TREBLE_LEVEL, 0)
    }

    fun setTrebleLevel(profile: Int, level: Int) {
        if (isReleased) return
        
        DolbyConstants.dlog(TAG, "setTrebleLevel: profile=$profile level=$level")

        if (level !in 0..100) {
            DolbyConstants.dlog(TAG, "setTrebleLevel: invalid level $level")
            throw IllegalArgumentException("Treble level must be between 0 and 100")
        }

        try {
            val prefs = getProfilePrefs(profile)
            val previousLevel = prefs.getInt(DolbyConstants.PREF_TREBLE_LEVEL, 0)

            prefs.edit().putInt(DolbyConstants.PREF_TREBLE_LEVEL, level).apply()
            setTrebleEnhancerEnabled(profile, level > 0)

            checkEffect()
            val currentGains = audioTuning.getRawGeqArray(profile)?.copyOf()
                ?: dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
            val modifiedGains = currentGains.copyOf()

            if (previousLevel > 0) {
                val previousGain = (previousLevel * TREBLE_GAIN_MULTIPLIER).toInt()
                for (i in 14..19) {
                    if (i < modifiedGains.size) {
                        modifiedGains[i] = clampGeqGain(modifiedGains[i] - previousGain)
                    }
                }
            }

            if (level > 0) {
                val trebleGain = (level * TREBLE_GAIN_MULTIPLIER).toInt()
                for (i in 14..19) {
                    if (i < modifiedGains.size) {
                        modifiedGains[i] = clampGeqGain(modifiedGains[i] + trebleGain)
                    }
                }
            }

            audioTuning.saveRawGeqArray(profile, modifiedGains)
            audioTuning.pushGeqToHardware(profile)
            
            DolbyConstants.dlog(TAG, "setTrebleLevel: success")
        } catch (e: IllegalArgumentException) {
            DolbyConstants.dlog(TAG, "setTrebleLevel: validation error - ${e.message}")
            val prefs = getProfilePrefs(profile)
            prefs.edit().putInt(DolbyConstants.PREF_TREBLE_LEVEL, 0).apply()
            throw e
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "setTrebleLevel: unexpected error - ${e.message}")
            throw e
        }
    }

    fun getVolumeLevelerEnabled(profile: Int): Boolean {
        if (!volumeLevelerSupported) return false
        return try {
            dolbyEffect.getDapParameterBool(DsParam.VOLUME_LEVELER_ENABLE, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting volume leveler: ${e.message}")
            false
        }
    }

    fun setVolumeLevelerEnabled(profile: Int, enabled: Boolean) {
        if (!volumeLevelerSupported || isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.VOLUME_LEVELER_ENABLE, enabled, profile)
            getProfilePrefs(profile).edit().putBoolean(DolbyConstants.PREF_VOLUME, enabled).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting volume leveler: ${e.message}")
        }
    }

    fun getIeqPreset(profile: Int): Int {
        return try {
            dolbyEffect.getDapParameterInt(DsParam.IEQ_PRESET, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting IEQ preset: ${e.message}")
            0
        }
    }

    fun setIeqPreset(profile: Int, preset: Int) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.IEQ_PRESET, preset, profile)
            getProfilePrefs(profile).edit().putString(DolbyConstants.PREF_IEQ, preset.toString()).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting IEQ preset: ${e.message}")
        }
    }

    fun getHeadphoneVirtualizerEnabled(profile: Int): Boolean {
        return try {
            dolbyEffect.getDapParameterBool(DsParam.HEADPHONE_VIRTUALIZER, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting headphone virtualizer: ${e.message}")
            false
        }
    }

    fun setHeadphoneVirtualizerEnabled(profile: Int, enabled: Boolean) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.HEADPHONE_VIRTUALIZER, enabled, profile)
            getProfilePrefs(profile).edit().putBoolean(DolbyConstants.PREF_HP_VIRTUALIZER, enabled).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting headphone virtualizer: ${e.message}")
        }
    }

    fun getSpeakerVirtualizerEnabled(profile: Int): Boolean {
        return try {
            dolbyEffect.getDapParameterBool(DsParam.SPEAKER_VIRTUALIZER, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting speaker virtualizer: ${e.message}")
            false
        }
    }

    fun setSpeakerVirtualizerEnabled(profile: Int, enabled: Boolean) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.SPEAKER_VIRTUALIZER, enabled, profile)
            getProfilePrefs(profile).edit().putBoolean(DolbyConstants.PREF_SPK_VIRTUALIZER, enabled).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting speaker virtualizer: ${e.message}")
        }
    }

    fun getStereoWideningAmount(profile: Int): Int {
        if (!stereoWideningSupported) return 0
        val prefs = getProfilePrefs(profile)
        if (prefs.contains(DolbyConstants.PREF_STEREO_WIDENING)) {
            return prefs.getInt(DolbyConstants.PREF_STEREO_WIDENING, 32)
        }
        return try {
            val amount = dolbyEffect.getDapParameterInt(DsParam.STEREO_WIDENING_AMOUNT, profile)
            prefs.edit().putInt(DolbyConstants.PREF_STEREO_WIDENING, amount).apply()
            amount
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting stereo widening: ${e.message}")
            32
        }
    }

    fun setStereoWideningAmount(profile: Int, amount: Int) {
        if (!stereoWideningSupported || isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.STEREO_WIDENING_AMOUNT, amount, profile)
            getProfilePrefs(profile).edit().putInt(DolbyConstants.PREF_STEREO_WIDENING, amount).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting stereo widening: ${e.message}")
        }
    }

    fun getDialogueEnhancerEnabled(profile: Int): Boolean {
        return try {
            dolbyEffect.getDapParameterBool(DsParam.DIALOGUE_ENHANCER_ENABLE, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting dialogue enhancer: ${e.message}")
            false
        }
    }

    fun setDialogueEnhancerEnabled(profile: Int, enabled: Boolean) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_ENABLE, enabled, profile)
            getProfilePrefs(profile).edit().putBoolean(DolbyConstants.PREF_DIALOGUE, enabled).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting dialogue enhancer: ${e.message}")
        }
    }

    fun getDialogueEnhancerAmount(profile: Int): Int {
        val prefs = getProfilePrefs(profile)
        if (prefs.contains(DolbyConstants.PREF_DIALOGUE_AMOUNT)) {
            return prefs.getInt(DolbyConstants.PREF_DIALOGUE_AMOUNT, 6)
        }
        return try {
            val amount = dolbyEffect.getDapParameterInt(DsParam.DIALOGUE_ENHANCER_AMOUNT, profile)
            prefs.edit().putInt(DolbyConstants.PREF_DIALOGUE_AMOUNT, amount).apply()
            amount
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting dialogue enhancer amount: ${e.message}")
            6
        }
    }

    fun setDialogueEnhancerAmount(profile: Int, amount: Int) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_AMOUNT, amount, profile)
            getProfilePrefs(profile).edit().putInt(DolbyConstants.PREF_DIALOGUE_AMOUNT, amount).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting dialogue enhancer amount: ${e.message}")
        }
    }

    fun getEqualizerGains(profile: Int, bandMode: BandMode): List<BandGain> {
        return try {
            val gains = audioTuning.getRawGeqArray(profile)
                ?: dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
            deserializeGains(gains, bandMode)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting equalizer gains: ${e.message}")
            val frequencies = when (bandMode) {
                BandMode.TEN_BAND -> BAND_FREQUENCIES_10
                BandMode.FIFTEEN_BAND -> BAND_FREQUENCIES_15
                BandMode.TWENTY_BAND -> BAND_FREQUENCIES_20
            }
            frequencies.map { BandGain(frequency = it, gain = 0) }
        }
    }

    fun setEqualizerGains(profile: Int, bandGains: List<BandGain>, bandMode: BandMode) {
        if (isReleased) return
        
        try {
            checkEffect()
            val gains = serializeGains(bandGains, bandMode)
            audioTuning.saveRawGeqArray(profile, gains)
            audioTuning.pushGeqToHardware(profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting equalizer gains: ${e.message}")
        }
    }

    fun getPresetName(profile: Int): String {
        return try {
            val gains = audioTuning.getRawGeqArray(profile)
                ?: dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
            
            val tenBandGains = gains.filterIndexed { index, _ -> index % 2 == 0 }
            val currentGainsString = tenBandGains.joinToString(",")
            
            val presetValues = context.resources.getStringArray(R.array.dolby_preset_values)
            val presetNames = context.resources.getStringArray(R.array.dolby_preset_entries)
            
            presetValues.forEachIndexed { index, preset ->
                val presetTenBand = convertTo10Band(preset)
                if (gainsMatch(presetTenBand, currentGainsString)) {
                    return presetNames[index]
                }
            }
            
            presetsPrefs.all.forEach { (name, value) ->
                val presetTenBand = convertTo10Band(value.toString())
                if (gainsMatch(presetTenBand, currentGainsString)) {
                    return name
                }
            }
            
            context.getString(R.string.dolby_preset_custom)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting preset name: ${e.message}")
            context.getString(R.string.dolby_preset_custom)
        }
    }

    private fun convertTo10Band(gainsString: String): String {
        val gains = gainsString.split(",").map { it.trim().toIntOrNull() ?: 0 }
        
        if (gains.size == 10) {
            return gains.joinToString(",")
        }
        
        if (gains.size == 20) {
            val tenBand = gains.filterIndexed { index, _ -> index % 2 == 0 }
            return tenBand.joinToString(",")
        }
        
        return gainsString
    }

    private fun gainsMatch(gains1: String, gains2: String): Boolean {
        val g1 = gains1.split(",").map { it.trim().toIntOrNull() ?: 0 }
        val g2 = gains2.split(",").map { it.trim().toIntOrNull() ?: 0 }
        
        if (g1.size != g2.size) return false
        
        return g1.zip(g2).all { (a, b) -> kotlin.math.abs(a - b) <= 1 }
    }

    fun getUserPresets(): List<EqualizerPreset> {
        synchronized(presetCacheLock) {
            cachedPresets?.let { return it }
            
            val bandMode = getBandMode()
            val presets = presetsPrefs.all.mapNotNull { (name, value) ->
                try {
                    val valueStr = value as? String ?: return@mapNotNull null
                    parsePreset(name, valueStr)
                } catch (e: Exception) {
                    DolbyConstants.dlog(TAG, "Error parsing preset $name: ${e.message}")
                    null
                }
            }
            
            cachedPresets = presets
            return presets
        }
    }

    private fun parsePreset(name: String, valueStr: String): EqualizerPreset? {
        return try {
            if (valueStr.contains("|")) {
                val parts = valueStr.split("|")
                val presetBandMode = BandMode.fromValue(parts[1])
                val gains = parts[0].split(",").map { it.toInt() }.toIntArray()
                EqualizerPreset(
                    name = name,
                    bandGains = deserializeGains(gains, presetBandMode),
                    isUserDefined = true,
                    bandMode = presetBandMode
                )
            } else {
                val gains = valueStr.split(",").map { it.toInt() }.toIntArray()
                EqualizerPreset(
                    name = name,
                    bandGains = deserializeGains(gains, BandMode.TEN_BAND),
                    isUserDefined = true,
                    bandMode = BandMode.TEN_BAND
                )
            }
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error parsing preset value: ${e.message}")
            null
        }
    }

    fun addUserPreset(name: String, bandGains: List<BandGain>, bandMode: BandMode) {
        try {
            val gains = serializeGains(bandGains, bandMode).joinToString(",")
            val value = "$gains|${bandMode.value}"
            presetsPrefs.edit().putString(name, value).apply()
            
            synchronized(presetCacheLock) {
                cachedPresets = null
            }
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error adding user preset: ${e.message}")
        }
    }

    fun deleteUserPreset(name: String) {
        try {
            presetsPrefs.edit().remove(name).apply()
            
            synchronized(presetCacheLock) {
                cachedPresets = null
            }
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error deleting user preset: ${e.message}")
        }
    }

    fun clearAllUserPresets() {
        try {
            presetsPrefs.edit().clear().apply()
            synchronized(presetCacheLock) {
                cachedPresets = null
            }
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error clearing user presets: ${e.message}")
        }
    }

    fun getCustomDolbyPresets(): List<CustomDolbyPresetSummary> {
        return customPresetsPrefs.all.mapNotNull { (name, value) ->
            try {
                val json = JSONObject(value as String)
                CustomDolbyPresetSummary(
                    name = name,
                    savedFromProfile = json.optInt("savedFromProfile", 0),
                    bandMode = BandMode.fromValue(json.optString("bandMode", "10")),
                    savedAt = json.optLong("savedAt", 0L)
                )
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error parsing custom preset $name: ${e.message}")
                null
            }
        }.sortedByDescending { it.savedAt }
    }

    fun saveCustomDolbyPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Preset name cannot be empty")
        }
        if (trimmed.length > 50) {
            throw IllegalArgumentException("Preset name too long")
        }
        if (customPresetsPrefs.contains(trimmed)) {
            throw IllegalArgumentException("Preset name already exists")
        }

        val profile = getCurrentProfile()
        val snapshot = JSONObject().apply {
            put("savedFromProfile", profile)
            put("bandMode", getBandMode().value)
            put("savedAt", System.currentTimeMillis())
            put("settings", exportProfilePrefsJson(profile))
        }
        customPresetsPrefs.edit().putString(trimmed, snapshot.toString()).apply()
        DolbyConstants.dlog(TAG, "Saved custom Dolby preset: $trimmed")
    }

    fun applyCustomDolbyPreset(name: String) {
        val jsonString = customPresetsPrefs.getString(name, null)
            ?: throw IllegalArgumentException("Preset not found")
        val json = JSONObject(jsonString)
        val profile = getCurrentProfile()
        val settings = json.getJSONObject("settings")
        restoreProfilePrefs(profile, settings)
        setBandMode(BandMode.fromValue(json.getString("bandMode")))
        restoreProfilePreset(profile)
        applyProfileSettings(profile)
        DolbyConstants.dlog(TAG, "Applied custom Dolby preset: $name to profile $profile")
    }

    fun deleteCustomDolbyPreset(name: String) {
        customPresetsPrefs.edit().remove(name).apply()
        DolbyConstants.dlog(TAG, "Deleted custom Dolby preset: $name")
    }

    fun exportFullBackupJson(): String {
        val profileIds = context.resources.getStringArray(R.array.dolby_profile_values)
            .map { it.toInt() }

        val global = JSONObject().apply {
            put(DolbyConstants.PREF_ENABLE, getDolbyEnabled())
            put(DolbyConstants.PREF_PROFILE, getCurrentProfile())
            put(DolbyConstants.PREF_BAND_MODE, getBandMode().value)
        }

        val profiles = JSONArray()
        profileIds.forEach { profileId ->
            profiles.put(exportProfilePrefsJson(profileId))
        }

        val userPresets = JSONArray()
        getUserPresets().forEach { preset ->
            userPresets.put(JSONObject().apply {
                put("name", preset.name)
                put("bandMode", preset.bandMode.value)
                val gainsArray = JSONArray()
                preset.bandGains.forEach { bandGain ->
                    gainsArray.put(JSONObject().apply {
                        put("frequency", bandGain.frequency)
                        put("gain", bandGain.gain)
                    })
                }
                put("bandGains", gainsArray)
            })
        }

        return JSONObject().apply {
            put("type", BACKUP_TYPE)
            put("version", BACKUP_VERSION)
            put("timestamp", System.currentTimeMillis())
            put("createdBy", "Lunaris Dolby Manager")
            put("global", global)
            put("profiles", profiles)
            put("user_presets", userPresets)
        }.toString(2)
    }

    fun importFullBackup(jsonString: String) {
        val json = JSONObject(jsonString)
        if (json.optString("type") != BACKUP_TYPE) {
            throw IllegalArgumentException("Not a Dolby full backup file")
        }
        val version = json.optInt("version", 0)
        if (version > BACKUP_VERSION) {
            throw IllegalArgumentException("Backup version not supported")
        }

        val global = json.getJSONObject("global")
        val bandMode = BandMode.fromValue(global.getString(DolbyConstants.PREF_BAND_MODE))
        setBandMode(bandMode)

        val profiles = json.getJSONArray("profiles")
        for (i in 0 until profiles.length()) {
            val profileJson = profiles.getJSONObject(i)
            val profileId = profileJson.getInt("id")
            restoreProfilePrefs(profileId, profileJson)
            applyProfileSettings(profileId)
        }

        if (json.has("user_presets")) {
            clearAllUserPresets()
            val presets = json.getJSONArray("user_presets")
            for (i in 0 until presets.length()) {
                val presetJson = presets.getJSONObject(i)
                val name = presetJson.getString("name")
                val presetBandMode = BandMode.fromValue(presetJson.getString("bandMode"))
                val gainsArray = presetJson.getJSONArray("bandGains")
                val bandGains = buildList {
                    for (j in 0 until gainsArray.length()) {
                        val gainObj = gainsArray.getJSONObject(j)
                        add(
                            BandGain(
                                frequency = gainObj.getInt("frequency"),
                                gain = gainObj.getInt("gain")
                            )
                        )
                    }
                }
                addUserPreset(name, bandGains, presetBandMode)
            }
        }

        val targetProfile = global.getInt(DolbyConstants.PREF_PROFILE)
        setCurrentProfile(targetProfile)
        setDolbyEnabled(global.getBoolean(DolbyConstants.PREF_ENABLE))
    }

    private fun exportProfilePrefsJson(profile: Int): JSONObject {
        val prefs = getProfilePrefs(profile)
        return JSONObject().apply {
            put("id", profile)
            prefs.all.forEach { (key, value) ->
                when (value) {
                    is Boolean -> put(key, value)
                    is Int -> put(key, value)
                    is Long -> put(key, value)
                    is Float -> put(key, value.toDouble())
                    is String -> put(key, value)
                }
            }
        }
    }

    private fun restoreProfilePrefs(profile: Int, json: JSONObject) {
        val editor = getProfilePrefs(profile).edit()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "id") continue
            when (val value = json.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Number -> editor.putInt(key, value.toInt())
            }
        }
        editor.apply()
    }

    fun resetProfile(profile: Int) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.resetProfileSpecificSettings(profile)
            context.deleteSharedPreferences("profile_$profile")
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error resetting profile: ${e.message}")
        }
    }

    fun resetAllProfiles() {
        if (isReleased) return
        
        try {
            checkEffect()
            context.resources.getStringArray(R.array.dolby_profile_values)
                .map { it.toInt() }
                .forEach { resetProfile(it) }
            setCurrentProfile(0)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error resetting all profiles: ${e.message}")
        }
    }

    private fun deserializeGains(gains: IntArray, bandMode: BandMode): List<BandGain> {
        val frequencies = when (bandMode) {
            BandMode.TEN_BAND -> BAND_FREQUENCIES_10
            BandMode.FIFTEEN_BAND -> BAND_FREQUENCIES_15
            BandMode.TWENTY_BAND -> BAND_FREQUENCIES_20
        }
        
        val indices = when (bandMode) {
            BandMode.TEN_BAND -> TEN_BAND_INDICES
            BandMode.FIFTEEN_BAND -> FIFTEEN_BAND_INDICES
            BandMode.TWENTY_BAND -> (0..19).toList()
        }
        
        return frequencies.mapIndexed { index, freq ->
            val gainIndex = indices.getOrNull(index) ?: index
            BandGain(frequency = freq, gain = gains.getOrElse(gainIndex) { 0 })
        }
    }

    private fun serializeGains(bandGains: List<BandGain>, bandMode: BandMode): IntArray {
        val result = IntArray(20) { 0 }
        
        when (bandMode) {
            BandMode.TEN_BAND -> {
                TEN_BAND_INDICES.forEachIndexed { index, targetIndex ->
                    if (index < bandGains.size && targetIndex < 20) {
                        result[targetIndex] = bandGains[index].gain
                    }
                }
                for (i in 0 until 19 step 2) {
                    if (i + 2 < 20) {
                        result[i + 1] = (result[i] + result[i + 2]) / 2
                    }
                }   
                result[19] = result[18]
            }
            BandMode.FIFTEEN_BAND -> {
                FIFTEEN_BAND_INDICES.forEachIndexed { index, targetIndex ->
                    if (index < bandGains.size && targetIndex < 20) {
                        result[targetIndex] = bandGains[index].gain
                    }
                }
                val missing = (0..19).filter { it !in FIFTEEN_BAND_INDICES }
                missing.forEach { idx ->
                    val prev = FIFTEEN_BAND_INDICES.filter { it < idx }.maxOrNull() ?: 0
                    val next = FIFTEEN_BAND_INDICES.filter { it > idx }.minOrNull() ?: 19
                    
                    if (prev < idx && next > idx && prev < 20 && next < 20) {
                        val prevValue = result[prev]
                        val nextValue = result[next]
                        val ratio = (idx - prev).toFloat() / (next - prev)
                        result[idx] = (prevValue + ratio * (nextValue - prevValue)).toInt()
                    } else if (prev < 20) {
                        result[idx] = result[prev]
                    }
                }
            }
            BandMode.TWENTY_BAND -> {
                bandGains.forEachIndexed { index, bandGain ->
                    if (index < 20) {
                        result[index] = bandGain.gain
                    }
                }
            }
        }
        
        return result
    }

    fun getMidEnhancerEnabled(profile: Int): Boolean {
        val prefs = getProfilePrefs(profile)
        return prefs.getBoolean(DolbyConstants.PREF_MID, false)
    }

    fun setMidEnhancerEnabled(profile: Int, enabled: Boolean) {
        getProfilePrefs(profile).edit().putBoolean(DolbyConstants.PREF_MID, enabled).apply()
    }

    fun getMidLevel(profile: Int): Int {
        val prefs = getProfilePrefs(profile)
        return prefs.getInt(DolbyConstants.PREF_MID_LEVEL, 0)
    }

    fun setMidLevel(profile: Int, level: Int) {
        if (isReleased) return
        
        DolbyConstants.dlog(TAG, "setMidLevel: profile=$profile level=$level")

        if (level !in 0..100) {
            DolbyConstants.dlog(TAG, "setMidLevel: invalid level $level")
            throw IllegalArgumentException("Mid level must be between 0 and 100")
        }

        try {
            val prefs = getProfilePrefs(profile)
            val previousLevel = prefs.getInt(DolbyConstants.PREF_MID_LEVEL, 0)

            prefs.edit().putInt(DolbyConstants.PREF_MID_LEVEL, level).apply()
            setMidEnhancerEnabled(profile, level > 0)

            checkEffect()
            val currentGains = audioTuning.getRawGeqArray(profile)?.copyOf()
                ?: dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
            val modifiedGains = currentGains.copyOf()

            if (previousLevel > 0) {
                val previousGain = (previousLevel * MID_GAIN_MULTIPLIER).toInt()
                for (i in 5..13) {
                    if (i < modifiedGains.size) {
                        modifiedGains[i] = clampGeqGain(modifiedGains[i] - previousGain)
                    }
                }
            }

            if (level > 0) {
                val midGain = (level * MID_GAIN_MULTIPLIER).toInt()
                for (i in 5..13) {
                    if (i < modifiedGains.size) {
                        modifiedGains[i] = clampGeqGain(modifiedGains[i] + midGain)
                    }
                }
            }

            audioTuning.saveRawGeqArray(profile, modifiedGains)
            audioTuning.pushGeqToHardware(profile)
            
            DolbyConstants.dlog(TAG, "setMidLevel: success")
        } catch (e: IllegalArgumentException) {
            DolbyConstants.dlog(TAG, "setMidLevel: validation error - ${e.message}")
            val prefs = getProfilePrefs(profile)
            prefs.edit().putInt(DolbyConstants.PREF_MID_LEVEL, 0).apply()
            throw e
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "setMidLevel: unexpected error - ${e.message}")
            throw e
        }
    }
    
    private fun release() {
        if (!isReleased) {
            DolbyConstants.dlog(TAG, "Releasing repository resources")
            isReleased = true
        }
    }
    
    override fun close() {
        release()
    }

    fun getAudioEnginePreferences(): AudioEnginePreferences = audioEnginePrefs

    fun getOutputBoostMaxTenths(): Int = audioTuning.getOutputBoostMaxTenths()
    fun getOutputBoostMinTenths(): Int = audioTuning.getOutputBoostMinTenths()

    fun getHeadroomInfo(profile: Int): HeadroomInfo {
        val raw = audioTuning.getRawGeqArray(profile) ?: IntArray(20)
        val outputDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull()
        val deviceOffset = if (outputDevice != null) {
            audioEnginePrefs.getDeviceGainOffsetTenths(deviceStateManager.deviceKey(outputDevice))
        } else 0
        return HeadroomCalculator.calculate(
            raw,
            getOutputBoostTenths(profile),
            isOutputBoostEnabled(profile),
            isVolmaxBoostEnabled(profile),
            getVolmaxBoost(profile),
            deviceOffset,
            audioEnginePrefs
        )
    }

    fun applyLoudnessPreset(preset: LoudnessPreset) {
        LoudnessPresetManager.applyToProfile(this, getCurrentProfile(), preset, audioEnginePrefs)
    }

    fun refreshSpatialAndGeq(profile: Int = getCurrentProfile()) {
        audioTuning.pushGeqToHardware(profile)
        audioTuning.applyAll(profile)
    }
    fun isOutputBoostEnabled(profile: Int) = audioTuning.isOutputBoostEnabled(profile)
    fun getOutputBoostTenths(profile: Int) = audioTuning.getOutputBoostTenths(profile)
    fun setOutputBoost(profile: Int, enabled: Boolean, tenthsDb: Int) =
        audioTuning.setOutputBoost(profile, enabled, tenthsDb)

    fun isVolmaxBoostEnabled(profile: Int) = audioTuning.isVolmaxBoostEnabled(profile)
    fun getVolmaxBoost(profile: Int) = audioTuning.getVolmaxBoost(profile)
    fun setVolmaxBoost(profile: Int, enabled: Boolean, value: Int) =
        audioTuning.setVolmaxBoost(profile, enabled, value)

    fun getIeqAmount(profile: Int) = audioTuning.getIeqAmount(profile)
    fun setIeqAmount(profile: Int, amount: Int) = audioTuning.setIeqAmount(profile, amount)

    fun isSurroundBoostEnabled(profile: Int) = audioTuning.isSurroundBoostEnabled(profile)
    fun getSurroundBoost(profile: Int) = audioTuning.getSurroundBoost(profile)
    fun setSurroundBoost(profile: Int, enabled: Boolean, value: Int) =
        audioTuning.setSurroundBoost(profile, enabled, value)

    fun getVolumeLevelerAmount(profile: Int) = audioTuning.getVolumeLevelerAmount(profile)
    fun setVolumeLevelerAmount(profile: Int, amount: Int) =
        audioTuning.setVolumeLevelerAmount(profile, amount)

    fun isVirtualBassEnabled(profile: Int) = audioTuning.isVirtualBassEnabled(profile)
    fun setVirtualBassEnabled(profile: Int, enabled: Boolean) =
        audioTuning.setVirtualBassEnabled(profile, enabled)
    fun isVirtualBassSpeakerEnabled(profile: Int) =
        audioTuning.isVirtualBassSpeakerEnabled(profile)
    fun setVirtualBassSpeakerEnabled(profile: Int, enabled: Boolean) =
        audioTuning.setVirtualBassSpeakerEnabled(profile, enabled)
    fun isVirtualBassBluetoothEnabled(profile: Int) =
        audioTuning.isVirtualBassBluetoothEnabled(profile)
    fun setVirtualBassBluetoothEnabled(profile: Int, enabled: Boolean) =
        audioTuning.setVirtualBassBluetoothEnabled(profile, enabled)
    fun reapplyVirtualBass(profile: Int = getCurrentProfile()) =
        audioTuning.reapplyVirtualBass(profile)

    fun isGraphicEqEnabled(profile: Int) = audioTuning.isGraphicEqEnabled(profile)
    fun setGraphicEqEnabled(profile: Int, enabled: Boolean) =
        audioTuning.setGraphicEqEnabled(profile, enabled)

    fun isDialogueDuckingEnabled(profile: Int) = audioTuning.isDialogueDuckingEnabled(profile)
    fun getDialogueDuckingAmount(profile: Int) = audioTuning.getDialogueDuckingAmount(profile)
    fun setDialogueDucking(profile: Int, enabled: Boolean, amount: Int) =
        audioTuning.setDialogueDucking(profile, enabled, amount)

    fun isLevelerTargetEnabled(profile: Int) = audioTuning.isLevelerTargetEnabled(profile)
    fun getLevelerTargetDb(profile: Int) = audioTuning.getLevelerTargetDb(profile)
    fun setLevelerTarget(profile: Int, enabled: Boolean, targetDb: Int) =
        audioTuning.setLevelerTarget(profile, enabled, targetDb)

    fun isAdvancedBassEnabled(profile: Int) = audioTuning.isAdvancedBassEnabled(profile)
    fun getAdvancedBassBoost(profile: Int) = audioTuning.getAdvancedBassBoost(profile)
    fun getAdvancedBassCutoff(profile: Int) = audioTuning.getAdvancedBassCutoff(profile)
    fun setAdvancedBass(profile: Int, enabled: Boolean, boostPercent: Int, cutoffHz: Int) =
        audioTuning.setAdvancedBass(profile, enabled, boostPercent, cutoffHz)

    fun isReverbSuppressionEnabled(profile: Int) = audioTuning.isReverbSuppressionEnabled(profile)
    fun getReverbSuppressionAmount(profile: Int) = audioTuning.getReverbSuppressionAmount(profile)
    fun setReverbSuppression(profile: Int, enabled: Boolean, amount: Int) =
        audioTuning.setReverbSuppression(profile, enabled, amount)

    fun isHearingProtectionEnabled(profile: Int) = audioTuning.isHearingProtectionEnabled(profile)
    fun setHearingProtectionEnabled(profile: Int, enabled: Boolean) =
        audioTuning.setHearingProtectionEnabled(profile, enabled)

    fun isDspVolumeBoostEnabled() = audioTuning.isDspVolumeBoostEnabled()
    fun getDspVolumeBoostStrength() = audioTuning.getDspVolumeBoostStrength()
    fun setDspVolumeBoost(enabled: Boolean, strength: Int) =
        audioTuning.setDspVolumeBoost(enabled, strength)

    companion object {
        private const val TAG = "DolbyRepository"
        private const val EFFECT_PRIORITY = 100
        private val EFFECT_LOCK = Any()

        @Volatile
        private var sharedDolbyEffect: DolbyAudioEffect? = null

        private fun getOrCreateDolbyEffect(): DolbyAudioEffect {
            sharedDolbyEffect?.let { return it }
            return synchronized(EFFECT_LOCK) {
                sharedDolbyEffect ?: try {
                    DolbyAudioEffect(EFFECT_PRIORITY, audioSession = 0).also {
                        sharedDolbyEffect = it
                    }
                } catch (e: Exception) {
                    DolbyConstants.dlog(TAG, "Failed to create shared Dolby effect: ${e.message}")
                    throw e
                }
            }
        }

        private const val BACKUP_TYPE = "dolby_full_backup"
        private const val BACKUP_VERSION = 1

        private val OUTPUT_DEVICE_PRIORITY = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        )
        
        private const val BASS_GAIN_MULTIPLIER = 1.4f
        private const val MID_GAIN_MULTIPLIER = 1.3f
        private const val TREBLE_GAIN_MULTIPLIER = 1.5f
        
        private val ATTRIBUTES_MEDIA = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val BAND_FREQUENCIES_10 = listOf(32, 64, 125, 250, 500, 1000, 2250, 5000, 10000, 19688)
        
        val BAND_FREQUENCIES_15 = listOf(
            32, 47, 94, 141, 234, 469, 844, 1313, 2250, 3750, 5813, 9000, 11250, 13875, 19688
        )
        
        val BAND_FREQUENCIES_20 = listOf(
            32, 47, 141, 234, 328, 469, 656, 844, 1031, 1313,
            1688, 2250, 3000, 3750, 4688, 5813, 7125, 9000, 11250, 19688
        )
        
        private val TEN_BAND_INDICES = listOf(0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
        
        private val FIFTEEN_BAND_INDICES = listOf(0, 1, 2, 3, 4, 5, 6, 8, 11, 12, 14, 15, 17, 18, 19)
        
        private val BASS_CURVES = listOf(
            floatArrayOf(
                1.00f, 1.00f, 0.95f, 0.90f, 0.80f, 0.70f, 0.55f, 0.40f, 0.25f, 0.15f,
                0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f
            ),
            floatArrayOf(
                1.20f, 1.15f, 1.05f, 0.90f, 0.70f, 0.55f, 0.40f, 0.25f, 0.10f, 0.05f,
                0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f
            ),
            floatArrayOf(
                0.90f, 0.95f, 1.00f, 1.00f, 0.90f, 0.75f, 0.60f, 0.45f, 0.30f, 0.20f,
                0.10f, 0.05f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f
            )
        )
    }
}
