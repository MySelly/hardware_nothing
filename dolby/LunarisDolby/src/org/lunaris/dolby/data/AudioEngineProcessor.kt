/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.audio.DolbyHalBridge

internal class AudioEngineProcessor(private val context: Context) {

    private val engine = AudioEnginePreferences(context)
    private val deviceStateManager = DeviceStateManager(context)

    fun currentDeviceKey(): String? {
        val device = deviceStateManager.getCurrentOutputDevice() ?: return null
        return deviceStateManager.deviceKey(device)
    }

    fun processGeqForHardware(raw: IntArray, profile: Int, outputBoostOffset: Int): IntArray {
        var gains = engine.applyHighPassIfNeeded(raw.copyOf())
        val deviceOffset = engine.getDeviceGainOffsetTenths(currentDeviceKey() ?: "")
        val combined = gains.map { it + outputBoostOffset + deviceOffset }.toIntArray()
        return if (engine.isSoftClipEnabled()) {
            engine.applySoftClipIfNeeded(combined)
        } else {
            combined.map { engine.clampEqGain(it) }.toIntArray()
        }
    }

    fun applySpatialProcessing(forceCrossfeed: Boolean = false) {
        val spatialMaster = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
            .getBoolean(DolbyConstants.PREF_SPATIAL_AUDIO_ENABLED, false)
        var crossfeedOn = engine.isCrossfeedEnabled()
        var crossfeedStrength = engine.getCrossfeedStrength()
        if ((forceCrossfeed || spatialMaster) && !crossfeedOn &&
            !engine.isStereoBalanceEnabled() && !engine.isMonoMixEnabled()
        ) {
            crossfeedOn = true
            if (crossfeedStrength <= 0) crossfeedStrength = 40
        } else if ((forceCrossfeed || spatialMaster) && engine.isCrossfeedEnabled()) {
            crossfeedOn = true
        }
        DolbyHalBridge.applySpatialAudio(
            context,
            engine.isStereoBalanceEnabled(),
            engine.getStereoBalance(),
            engine.isMonoMixEnabled(),
            crossfeedOn,
            crossfeedStrength
        )
    }

    fun applyAutoLoudness(volumeStep: Int, maxSteps: Int, profile: Int, repository: DolbyRepository) {
        if (!engine.isAutoLoudnessEnabled()) return
        val fraction = volumeStep.toFloat() / maxSteps.coerceAtLeast(1)
        val tenths = when {
            fraction < 0.3f -> (engine.getOutputBoostMaxTenths() * (0.3f - fraction)).toInt()
            fraction > 0.85f -> 0
            else -> 0
        }
        if (tenths > 0) {
            repository.setOutputBoost(profile, true, tenths.coerceAtMost(engine.getOutputBoostMaxTenths()))
        } else {
            // High volume, mid range, or zero tenths after a prior boost — clear leftover boost.
            repository.setOutputBoost(profile, false, 0)
        }
    }

    fun preferences(): AudioEnginePreferences = engine
}
