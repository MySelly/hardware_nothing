/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import android.content.SharedPreferences
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.DolbyConstants.DsParam
import org.lunaris.dolby.audio.DolbyAudioEffect
import org.lunaris.dolby.audio.DolbyHalBridge

internal class AudioTuningController(
    private val context: Context,
    private val dolbyEffectProvider: () -> DolbyAudioEffect,
    private val checkEffect: () -> Unit,
    private val isReleased: () -> Boolean,
    private val profilePrefs: (Int) -> SharedPreferences,
    private val defaultPrefs: SharedPreferences
) {

    fun applyAll(profile: Int) {
        if (isReleased()) return
        pushGeqToHardware(profile)
        applyVolmaxBoost(profile)
        applyIeqAmount(profile)
        applySurroundBoost(profile)
        applyVolumeLevelerAmount(profile)
        applyVirtualBass(profile)
        applyHearingProtection(profile)
    }

    fun pushGeqToHardware(profile: Int) {
        if (isReleased()) return
        try {
            checkEffect()
            val raw = getRawGeqArray(profile) ?: return
            val offset = getOutputBoostOffset(profile)
            val applied = raw.map { (it + offset).coerceIn(-150, 150) }.toIntArray()
            dolbyEffectProvider().setDapParameter(DsParam.GEQ_BAND_GAINS, applied, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "pushGeqToHardware failed: ${e.message}")
        }
    }

    fun getRawGeqArray(profile: Int): IntArray? {
        val saved = profilePrefs(profile).getString(DolbyConstants.PREF_PRESET, null)
        if (saved != null) {
            val arr = saved.split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
            if (arr.size == 20) return arr
        }
        return try {
            checkEffect()
            dolbyEffectProvider().getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "getRawGeqArray fallback failed: ${e.message}")
            null
        }
    }

    fun saveRawGeqArray(profile: Int, gains: IntArray) {
        profilePrefs(profile).edit()
            .putString(DolbyConstants.PREF_PRESET, gains.joinToString(","))
            .apply()
    }

    // --- Output boost (-6..+6 dB) ---

    fun isOutputBoostEnabled(profile: Int): Boolean =
        profilePrefs(profile).getBoolean(DolbyConstants.PREF_OUTPUT_BOOST_ENABLED, false)

    fun getOutputBoostTenths(profile: Int): Int =
        profilePrefs(profile).getInt(DolbyConstants.PREF_OUTPUT_BOOST_TENTHS, 0)
            .coerceIn(DolbyConstants.OUTPUT_BOOST_MIN_TENTHS, DolbyConstants.OUTPUT_BOOST_MAX_TENTHS)

    fun getOutputBoostOffset(profile: Int): Int =
        if (isOutputBoostEnabled(profile)) getOutputBoostTenths(profile) else 0

    fun setOutputBoost(profile: Int, enabled: Boolean, tenthsDb: Int) {
        if (isReleased()) return
        val clamped = tenthsDb.coerceIn(
            DolbyConstants.OUTPUT_BOOST_MIN_TENTHS,
            DolbyConstants.OUTPUT_BOOST_MAX_TENTHS
        )
        profilePrefs(profile).edit()
            .putBoolean(DolbyConstants.PREF_OUTPUT_BOOST_ENABLED, enabled)
            .putInt(DolbyConstants.PREF_OUTPUT_BOOST_TENTHS, clamped)
            .apply()
        pushGeqToHardware(profile)
    }

    // --- Volmax boost ---

    fun isVolmaxBoostEnabled(profile: Int): Boolean =
        profilePrefs(profile).getBoolean(DolbyConstants.PREF_VOLMAX_BOOST_ENABLED, false)

    fun getVolmaxBoost(profile: Int): Int =
        profilePrefs(profile).getInt(DolbyConstants.PREF_VOLMAX_BOOST, 48)
            .coerceIn(0, DolbyConstants.VOLMAX_BOOST_MAX)

    fun setVolmaxBoost(profile: Int, enabled: Boolean, value: Int) {
        if (isReleased()) return
        val clamped = value.coerceIn(0, DolbyConstants.VOLMAX_BOOST_MAX)
        profilePrefs(profile).edit()
            .putBoolean(DolbyConstants.PREF_VOLMAX_BOOST_ENABLED, enabled)
            .putInt(DolbyConstants.PREF_VOLMAX_BOOST, clamped)
            .apply()
        applyVolmaxBoost(profile)
    }

    private fun applyVolmaxBoost(profile: Int) {
        val enabled = isVolmaxBoostEnabled(profile)
        val value = getVolmaxBoost(profile)
        setDapInt(DsParam.VOLMAX_BOOST, profile, if (enabled) value else 0)
        DolbyHalBridge.applyVolmaxBoost(context, enabled, value)
    }

    // --- IEQ amount ---

    fun getIeqAmount(profile: Int): Int {
        val prefs = profilePrefs(profile)
        if (prefs.contains(DolbyConstants.PREF_IEQ_AMOUNT)) {
            return prefs.getInt(DolbyConstants.PREF_IEQ_AMOUNT, 6)
        }
        return readDapInt(DsParam.IEQ_AMOUNT, profile, 6)
            .also { prefs.edit().putInt(DolbyConstants.PREF_IEQ_AMOUNT, it).apply() }
    }

    fun setIeqAmount(profile: Int, amount: Int) {
        if (isReleased()) return
        val clamped = amount.coerceIn(0, DolbyConstants.IEQ_AMOUNT_MAX)
        profilePrefs(profile).edit().putInt(DolbyConstants.PREF_IEQ_AMOUNT, clamped).apply()
        setDapInt(DsParam.IEQ_AMOUNT, profile, clamped)
    }

    // --- Surround boost ---

    fun isSurroundBoostEnabled(profile: Int): Boolean =
        profilePrefs(profile).getBoolean(DolbyConstants.PREF_SURROUND_BOOST_ENABLED, false)

    fun getSurroundBoost(profile: Int): Int =
        profilePrefs(profile).getInt(DolbyConstants.PREF_SURROUND_BOOST, 0)
            .coerceIn(0, DolbyConstants.SURROUND_BOOST_MAX)

    fun setSurroundBoost(profile: Int, enabled: Boolean, value: Int) {
        if (isReleased()) return
        val clamped = value.coerceIn(0, DolbyConstants.SURROUND_BOOST_MAX)
        profilePrefs(profile).edit()
            .putBoolean(DolbyConstants.PREF_SURROUND_BOOST_ENABLED, enabled)
            .putInt(DolbyConstants.PREF_SURROUND_BOOST, clamped)
            .apply()
        setDapInt(DsParam.SURROUND_BOOST, profile, if (enabled) clamped else 0)
    }

    // --- Volume leveler amount ---

    fun getVolumeLevelerAmount(profile: Int): Int {
        val prefs = profilePrefs(profile)
        if (prefs.contains(DolbyConstants.PREF_VOLUME_LEVELER_AMOUNT)) {
            return prefs.getInt(DolbyConstants.PREF_VOLUME_LEVELER_AMOUNT, 0)
        }
        return readDapInt(DsParam.VOLUME_LEVELER_AMOUNT, profile, 0)
            .also { prefs.edit().putInt(DolbyConstants.PREF_VOLUME_LEVELER_AMOUNT, it).apply() }
    }

    fun setVolumeLevelerAmount(profile: Int, amount: Int) {
        if (isReleased()) return
        val clamped = amount.coerceIn(0, DolbyConstants.VOLUME_LEVELER_AMOUNT_MAX)
        profilePrefs(profile).edit().putInt(DolbyConstants.PREF_VOLUME_LEVELER_AMOUNT, clamped).apply()
        setDapInt(DsParam.VOLUME_LEVELER_AMOUNT, profile, clamped)
    }

    // --- Virtual bass ---

    fun isVirtualBassEnabled(profile: Int): Boolean =
        profilePrefs(profile).getBoolean(DolbyConstants.PREF_VIRTUAL_BASS, false)

    fun setVirtualBassEnabled(profile: Int, enabled: Boolean) {
        if (isReleased()) return
        profilePrefs(profile).edit().putBoolean(DolbyConstants.PREF_VIRTUAL_BASS, enabled).apply()
        applyVirtualBass(profile)
    }

    private fun applyVirtualBass(profile: Int) {
        val enabled = isVirtualBassEnabled(profile)
        setDapInt(DsParam.VIRTUAL_BASS_ENABLE, profile, if (enabled) 1 else 0)
        DolbyHalBridge.applyVirtualBassHal(context, enabled)
    }

    // --- Hearing protection ---

    fun isHearingProtectionEnabled(profile: Int): Boolean =
        profilePrefs(profile).getBoolean(DolbyConstants.PREF_HEARING_PROTECTION, false)

    fun setHearingProtectionEnabled(profile: Int, enabled: Boolean) {
        if (isReleased()) return
        profilePrefs(profile).edit().putBoolean(DolbyConstants.PREF_HEARING_PROTECTION, enabled).apply()
        applyHearingProtection(profile)
    }

    private fun applyHearingProtection(profile: Int) {
        val enabled = isHearingProtectionEnabled(profile)
        setDapInt(DsParam.HEARING_PROTECTION_ENABLE, profile, if (enabled) 1 else 0)
        DolbyHalBridge.applyHearingProtection(context, enabled)
    }

    // --- DSP volume boost (global) ---

    fun isDspVolumeBoostEnabled(): Boolean =
        defaultPrefs.getBoolean(DolbyConstants.PREF_DSP_VOLUME_BOOST_ENABLED, false)

    fun getDspVolumeBoostStrength(): Int =
        defaultPrefs.getInt(DolbyConstants.PREF_DSP_VOLUME_BOOST_STRENGTH, 0).coerceIn(0, 100)

    fun setDspVolumeBoost(enabled: Boolean, strength: Int) {
        val clamped = strength.coerceIn(0, 100)
        defaultPrefs.edit()
            .putBoolean(DolbyConstants.PREF_DSP_VOLUME_BOOST_ENABLED, enabled)
            .putInt(DolbyConstants.PREF_DSP_VOLUME_BOOST_STRENGTH, clamped)
            .apply()
    }

    private fun applyIeqAmount(profile: Int) {
        setDapInt(DsParam.IEQ_AMOUNT, profile, getIeqAmount(profile))
    }

    private fun applySurroundBoost(profile: Int) {
        val enabled = isSurroundBoostEnabled(profile)
        setDapInt(DsParam.SURROUND_BOOST, profile, if (enabled) getSurroundBoost(profile) else 0)
    }

    private fun applyVolumeLevelerAmount(profile: Int) {
        setDapInt(DsParam.VOLUME_LEVELER_AMOUNT, profile, getVolumeLevelerAmount(profile))
    }

    private fun readDapInt(param: DsParam, profile: Int, default: Int): Int = try {
        checkEffect()
        dolbyEffectProvider().getDapParameterInt(param, profile)
    } catch (_: Exception) {
        default
    }

    private fun setDapInt(param: DsParam, profile: Int, value: Int) {
        try {
            checkEffect()
            dolbyEffectProvider().setDapParameter(param, value, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "setDapInt $param=$value failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AudioTuning"
    }
}
