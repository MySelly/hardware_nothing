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

    fun preferences(): AudioEnginePreferences = engine
}
