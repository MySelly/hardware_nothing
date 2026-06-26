/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import org.lunaris.dolby.domain.models.ProfileSettings

enum class LoudnessPreset(val key: String) {
    FLAT("flat"),
    PUNCH("punch"),
    WARM("warm"),
    MAX("max");

    companion object {
        fun fromKey(key: String): LoudnessPreset? = entries.find { it.key == key }
    }
}

object LoudnessPresetManager {

    data class LoudnessApplyResult(
        val outputBoostEnabled: Boolean,
        val outputBoostTenths: Int,
        val volmaxEnabled: Boolean,
        val volmaxValue: Int,
        val virtualBassEnabled: Boolean,
        val bassLevel: Int,
        val bassCurve: Int,
        val volumeLevelerEnabled: Boolean
    )

    fun buildForPreset(preset: LoudnessPreset, engine: AudioEnginePreferences): LoudnessApplyResult {
        val maxBoost = if (engine.isExtendedOutputBoostEnabled()) {
            engine.getOutputBoostMaxTenths()
        } else {
            30 // +3 dB default when extended off
        }
        return when (preset) {
            LoudnessPreset.FLAT -> LoudnessApplyResult(
                outputBoostEnabled = false,
                outputBoostTenths = 0,
                volmaxEnabled = false,
                volmaxValue = 0,
                virtualBassEnabled = false,
                bassLevel = 0,
                bassCurve = 0,
                volumeLevelerEnabled = false
            )
            LoudnessPreset.PUNCH -> LoudnessApplyResult(
                outputBoostEnabled = true,
                outputBoostTenths = (maxBoost * 0.5f).toInt().coerceAtMost(80),
                volmaxEnabled = true,
                volmaxValue = 48,
                virtualBassEnabled = true,
                bassLevel = 55,
                bassCurve = 2,
                volumeLevelerEnabled = false
            )
            LoudnessPreset.WARM -> LoudnessApplyResult(
                outputBoostEnabled = true,
                outputBoostTenths = (maxBoost * 0.35f).toInt().coerceAtMost(50),
                volmaxEnabled = false,
                volmaxValue = 0,
                virtualBassEnabled = true,
                bassLevel = 45,
                bassCurve = 0,
                volumeLevelerEnabled = true
            )
            LoudnessPreset.MAX -> LoudnessApplyResult(
                outputBoostEnabled = true,
                outputBoostTenths = maxBoost,
                volmaxEnabled = true,
                volmaxValue = 72,
                virtualBassEnabled = true,
                bassLevel = 70,
                bassCurve = 2,
                volumeLevelerEnabled = false
            )
        }
    }

    fun applyToProfile(
        repository: DolbyRepository,
        profile: Int,
        preset: LoudnessPreset,
        engine: AudioEnginePreferences
    ) {
        if (!engine.isLoudnessPresetsEnabled()) return
        val config = buildForPreset(preset, engine)
        repository.setOutputBoost(profile, config.outputBoostEnabled, config.outputBoostTenths)
        repository.setVolmaxBoost(profile, config.volmaxEnabled, config.volmaxValue)
        repository.setVirtualBassEnabled(profile, config.virtualBassEnabled)
        repository.setBassLevel(profile, config.bassLevel)
        repository.setBassCurve(profile, config.bassCurve)
        repository.setVolumeLevelerEnabled(profile, config.volumeLevelerEnabled)
    }
}
