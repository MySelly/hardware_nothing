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
            "DolbyVolMaxBoost=$boost"
        ))
        DolbyConstants.dlog(TAG, "volmax_boost=$boost enabled=$enabled")
    }

    fun applyHearingProtection(context: Context, enabled: Boolean) {
        val flag = if (enabled) 1 else 0
        setParameters(context, listOf(
            "dolby_hearing_protection=$flag",
            "hearing_protection_enable=$flag"
        ))
        DolbyConstants.dlog(TAG, "hearing_protection=$flag")
    }

    fun applyVirtualBassHal(context: Context, enabled: Boolean) {
        val flag = if (enabled) 1 else 0
        setParameters(context, listOf(
            "dolby_virtual_bass=$flag",
            "virtual_bass_process_enable=$flag"
        ))
        DolbyConstants.dlog(TAG, "virtual_bass=$flag")
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
