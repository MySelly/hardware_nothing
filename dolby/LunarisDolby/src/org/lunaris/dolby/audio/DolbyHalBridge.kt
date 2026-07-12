/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.audio

import android.content.Context
import android.media.AudioManager
import org.lunaris.dolby.DolbyConstants

/**
 * Vendor HAL parameter bridge for DAX settings not exposed via AudioEffect DsParam.
 */
object DolbyHalBridge {

    private fun audioManager(context: Context): AudioManager? =
        context.getSystemService(AudioManager::class.java)

    fun applyVolmaxBoost(context: Context, enabled: Boolean, value: Int) {
        val boost = if (enabled) value.coerceIn(0, 96) else 0
        setParameters(context, listOf(
            "dolby_volmax_boost=$boost",
            "volmax_boost=$boost",
            "volmax-boost=$boost",
            "DolbyVolMaxBoost=$boost"
        ))
        DolbyConstants.dlog(TAG, "volmax_boost=$boost enabled=$enabled")
    }

    fun applySurroundBoost(context: Context, enabled: Boolean, value: Int) {
        val boost = if (enabled) value.coerceIn(0, 64) else 0
        setParameters(context, listOf(
            "dolby_surround_boost=$boost",
            "surround_boost=$boost",
            "surround-boost=$boost",
            "DolbySurroundBoost=$boost"
        ))
        DolbyConstants.dlog(TAG, "surround_boost=$boost enabled=$enabled")
    }

    fun applyHearingProtection(context: Context, enabled: Boolean) {
        val flag = if (enabled) 1 else 0
        setParameters(context, listOf(
            "dolby_hearing_protection=$flag",
            "hearing_protection_enable=$flag",
            "hearing-protection-enable=$flag"
        ))
        DolbyConstants.dlog(TAG, "hearing_protection=$flag")
    }

    fun applyVirtualBassHal(context: Context, enabled: Boolean) {
        val flag = if (enabled) 1 else 0
        setParameters(context, listOf(
            "dolby_virtual_bass=$flag",
            "virtual_bass_process_enable=$flag",
            "virtual-bass-process-enable=$flag",
            "virtual_bass_process=$flag"
        ))
        DolbyConstants.dlog(TAG, "virtual_bass=$flag")
    }

    fun applySpatialAudio(
        context: Context,
        balanceEnabled: Boolean,
        balance: Int,
        monoEnabled: Boolean,
        crossfeedEnabled: Boolean,
        crossfeedStrength: Int
    ) {
        val balanceVal = if (balanceEnabled) balance.coerceIn(-100, 100) else 0
        val mono = if (monoEnabled) 1 else 0
        val crossfeed = if (crossfeedEnabled) crossfeedStrength.coerceIn(0, 100) else 0
        setParameters(context, listOf(
            "stereo_balance=$balanceVal",
            "dolby_stereo_balance=$balanceVal",
            "mono_mix=$mono",
            "dolby_mono_mix=$mono",
            "crossfeed=$crossfeed",
            "dolby_crossfeed=$crossfeed"
        ))
        DolbyConstants.dlog(TAG, "spatial balance=$balanceVal mono=$mono crossfeed=$crossfeed")
    }

    fun syncDspVolume(context: Context, volumeStep: Int, boostEnabled: Boolean, boostStrength: Int) {
        val am = audioManager(context) ?: return
        val strength = boostStrength.coerceIn(0, 100)
        if (!boostEnabled || strength == 0) {
            am.setParameters("volume_change=$volumeStep;flags=8")
            return
        }
        val maxSteps = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val fraction = volumeStep.toFloat() / maxSteps.toFloat()
        val extra = (fraction * strength / 100f * maxSteps * 0.35f).toInt()
        val scaled = (volumeStep + extra).coerceIn(0, maxSteps)
        am.setParameters("volume_change=$scaled;flags=8")
        am.setParameters("dsp_loudness_boost=$strength")
        DolbyConstants.dlog(TAG, "dsp volume step=$volumeStep scaled=$scaled boost=$strength")
    }

    private fun setParameters(context: Context, params: List<String>) {
        val am = audioManager(context) ?: return
        params.forEach { param ->
            try {
                am.setParameters(param)
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "setParameters failed for $param: ${e.message}")
            }
        }
    }

    private const val TAG = "DolbyHalBridge"
}
