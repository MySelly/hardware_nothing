/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby

import android.util.Log

object DolbyConstants {

    const val TAG = "Dolby"
    
    const val PREF_ENABLE = "dolby_enable"
    const val PREF_PROFILE = "dolby_profile"
    const val PREF_PRESET = "dolby_preset"
    const val PREF_IEQ = "dolby_ieq"
    const val PREF_HP_VIRTUALIZER = "dolby_virtualizer"
    const val PREF_SPK_VIRTUALIZER = "dolby_spk_virtualizer"
    const val PREF_STEREO_WIDENING = "dolby_stereo_widening"
    const val PREF_DIALOGUE = "dolby_dialogue_enabled"
    const val PREF_DIALOGUE_AMOUNT = "dolby_dialogue_amount"
    const val PREF_BASS = "dolby_bass"
    const val PREF_BASS_LEVEL = "dolby_bass_level"
    const val PREF_BASS_CURVE = "dolby_bass_curve"
    const val PREF_MID = "dolby_mid"
    const val PREF_MID_LEVEL = "dolby_mid_level"
    const val PREF_TREBLE = "dolby_treble"
    const val PREF_TREBLE_LEVEL = "dolby_treble_level"
    const val PREF_VOLUME = "dolby_volume"
    const val PREF_VOLUME_LEVELER_AMOUNT = "dolby_volume_leveler_amount"
    const val PREF_OUTPUT_BOOST_ENABLED = "dolby_output_boost_enabled"
    const val PREF_OUTPUT_BOOST_TENTHS = "dolby_output_boost_tenths"
    const val PREF_VOLMAX_BOOST_ENABLED = "dolby_volmax_boost_enabled"
    const val PREF_VOLMAX_BOOST = "dolby_volmax_boost"
    const val PREF_IEQ_AMOUNT = "dolby_ieq_amount"
    const val PREF_SURROUND_BOOST_ENABLED = "dolby_surround_boost_enabled"
    const val PREF_SURROUND_BOOST = "dolby_surround_boost"
    const val PREF_VIRTUAL_BASS = "dolby_virtual_bass"
    const val PREF_HEARING_PROTECTION = "dolby_hearing_protection"
    const val PREF_DSP_VOLUME_BOOST_ENABLED = "dsp_volume_boost_enabled"
    const val PREF_DSP_VOLUME_BOOST_STRENGTH = "dsp_volume_boost_strength"
    const val PREF_PRESETS_MIGRATED = "presets_migrated"
    const val PREF_BAND_MODE = "dolby_band_mode"
    const val PREF_DEVICE_STATE_MEMORY = "device_state_memory_enabled"
    const val PREF_PROFILE_PRIORITY = "profile_priority"
    const val PREF_SCHEDULED_PROFILES = "scheduled_profiles"
    const val PREF_SCHEDULED_PROFILES_ENABLED = "scheduled_profiles_enabled"

    const val PROFILE_PRIORITY_DEVICE = "device"
    const val PROFILE_PRIORITY_APP = "app"
    
    const val PREF_FILE_PRESETS = "presets"
    const val PREF_FILE_CUSTOM_PRESETS = "custom_dolby_presets"

    enum class DsParam(val id: Int, val length: Int = 1) {
        HEADPHONE_VIRTUALIZER(101),
        SPEAKER_VIRTUALIZER(102),
        VOLUME_LEVELER_ENABLE(103),
        IEQ_PRESET(104),
        DIALOGUE_ENHANCER_ENABLE(105),
        DIALOGUE_ENHANCER_AMOUNT(108),
        GEQ_BAND_GAINS(110, 20),
        BASS_ENHANCER_ENABLE(111),
        STEREO_WIDENING_AMOUNT(113),
        VOLMAX_BOOST(114),
        IEQ_ENABLE(106),
        IEQ_AMOUNT(107),
        VOLUME_LEVELER_AMOUNT(109),
        SURROUND_BOOST(112),
        VIRTUAL_BASS_ENABLE(117),
        HEARING_PROTECTION_ENABLE(118);

        override fun toString(): String = "${name}(${id})"
    }

    const val OUTPUT_BOOST_MIN_TENTHS = -60
    const val OUTPUT_BOOST_MAX_TENTHS = 60
    const val VOLMAX_BOOST_MAX = 96
    const val SURROUND_BOOST_MAX = 64
    const val IEQ_AMOUNT_MAX = 10
    const val VOLUME_LEVELER_AMOUNT_MAX = 10

    fun dlog(tag: String, msg: String) {
        if (Log.isLoggable(TAG, Log.DEBUG) || Log.isLoggable(tag, Log.DEBUG)) {
            Log.d("$TAG-$tag", msg)
        }
    }
}
