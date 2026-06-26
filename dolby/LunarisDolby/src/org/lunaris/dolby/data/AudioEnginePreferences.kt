/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import org.json.JSONObject
import org.lunaris.dolby.DolbyConstants
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * User-configurable audio engine limits and optional processing toggles.
 * Every extended feature is off by default; users opt in per feature.
 */
class AudioEnginePreferences(context: Context) {

    private val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)

    // --- EQ range ---
    fun isExtendedEqEnabled(): Boolean =
        prefs.getBoolean(DolbyConstants.PREF_EXTENDED_EQ_ENABLED, false)

    fun setExtendedEqEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(DolbyConstants.PREF_EXTENDED_EQ_ENABLED, enabled).apply()
    }

    fun getEqRangePreset(): EqRangePreset {
        if (!isExtendedEqEnabled()) return EqRangePreset.STANDARD
        return EqRangePreset.fromKey(
            prefs.getString(DolbyConstants.PREF_EQ_RANGE_PRESET, EqRangePreset.STANDARD.key)
        )
    }

    fun setEqRangePreset(preset: EqRangePreset) {
        prefs.edit().putString(DolbyConstants.PREF_EQ_RANGE_PRESET, preset.key).apply()
    }

    fun getEqGainMax(): Int = getEqRangePreset().maxRaw

    fun clampEqGain(raw: Int): Int = raw.coerceIn(-getEqGainMax(), getEqGainMax())

    // --- Output boost range ---
    fun isExtendedOutputBoostEnabled(): Boolean =
        prefs.getBoolean(DolbyConstants.PREF_EXTENDED_OUTPUT_BOOST_ENABLED, false)

    fun setExtendedOutputBoostEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(DolbyConstants.PREF_EXTENDED_OUTPUT_BOOST_ENABLED, enabled).apply()
    }

    fun getOutputBoostMaxTenths(): Int {
        if (!isExtendedOutputBoostEnabled()) return DolbyConstants.OUTPUT_BOOST_STANDARD_MAX
        return prefs.getInt(
            DolbyConstants.PREF_OUTPUT_BOOST_MAX_TENTHS_USER,
            DolbyConstants.OUTPUT_BOOST_EXTENDED_MAX
        ).coerceIn(DolbyConstants.OUTPUT_BOOST_STANDARD_MAX, DolbyConstants.OUTPUT_BOOST_EXTENDED_MAX)
    }

    fun getOutputBoostMinTenths(): Int {
        if (!isExtendedOutputBoostEnabled()) return DolbyConstants.OUTPUT_BOOST_MIN_TENTHS
        return -getOutputBoostMaxTenths()
    }

    fun setOutputBoostMaxTenths(maxTenths: Int) {
        prefs.edit().putInt(
            DolbyConstants.PREF_OUTPUT_BOOST_MAX_TENTHS_USER,
            maxTenths.coerceIn(DolbyConstants.OUTPUT_BOOST_STANDARD_MAX, DolbyConstants.OUTPUT_BOOST_EXTENDED_MAX)
        ).apply()
    }

    fun clampOutputBoostTenths(tenths: Int): Int =
        tenths.coerceIn(getOutputBoostMinTenths(), getOutputBoostMaxTenths())

    // --- Processing toggles ---
    fun isHeadroomWarningEnabled(): Boolean =
        prefs.getBoolean(DolbyConstants.PREF_HEADROOM_WARNING_ENABLED, true)

    fun setHeadroomWarningEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(DolbyConstants.PREF_HEADROOM_WARNING_ENABLED, enabled).apply()
    }

    fun isSoftClipEnabled(): Boolean =
        prefs.getBoolean(DolbyConstants.PREF_SOFT_CLIP_ENABLED, false)

    fun setSoftClipEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(DolbyConstants.PREF_SOFT_CLIP_ENABLED, enabled).apply()
    }

    fun isLoudnessPresetsEnabled(): Boolean =
        prefs.getBoolean(DolbyConstants.PREF_LOUDNESS_PRESETS_ENABLED, true)

    fun isAutoLoudnessEnabled(): Boolean =
        prefs.getBoolean(DolbyConstants.PREF_AUTO_LOUDNESS_ENABLED, false)

    fun setAutoLoudnessEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(DolbyConstants.PREF_AUTO_LOUDNESS_ENABLED, enabled).apply()
    }

    fun isPerDeviceGainEnabled(): Boolean =
        prefs.getBoolean(DolbyConstants.PREF_PER_DEVICE_GAIN_ENABLED, false)

    fun isStereoBalanceEnabled(): Boolean =
        prefs.getBoolean(DolbyConstants.PREF_STEREO_BALANCE_ENABLED, false)

    fun getStereoBalance(): Int =
        prefs.getInt(DolbyConstants.PREF_STEREO_BALANCE_VALUE, 0).coerceIn(-100, 100)

    fun isMonoMixEnabled(): Boolean =
        prefs.getBoolean(DolbyConstants.PREF_MONO_MIX_ENABLED, false)

    fun isCrossfeedEnabled(): Boolean =
        prefs.getBoolean(DolbyConstants.PREF_CROSSFEED_ENABLED, false)

    fun getCrossfeedStrength(): Int =
        prefs.getInt(DolbyConstants.PREF_CROSSFEED_STRENGTH, 50).coerceIn(0, 100)

    fun isHighPassEnabled(): Boolean =
        prefs.getBoolean(DolbyConstants.PREF_HIGH_PASS_ENABLED, false)

    fun getHighPassHz(): Int =
        prefs.getInt(DolbyConstants.PREF_HIGH_PASS_HZ, 40).coerceIn(20, 120)

    fun isEqSpectrumOverlayEnabled(): Boolean =
        prefs.getBoolean(DolbyConstants.PREF_EQ_SPECTRUM_OVERLAY, false)

    fun isEqColoredDbLabelsEnabled(): Boolean =
        prefs.getBoolean(DolbyConstants.PREF_EQ_COLORED_DB_LABELS, true)

    fun isLoudnessStackVisualEnabled(): Boolean =
        prefs.getBoolean(DolbyConstants.PREF_LOUDNESS_STACK_VISUAL, true)

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getDeviceGainOffsetTenths(deviceKey: String): Int {
        if (!isPerDeviceGainEnabled()) return 0
        val raw = prefs.getString(DolbyConstants.PREF_DEVICE_GAIN_OFFSETS, null) ?: return 0
        return try {
            JSONObject(raw).optInt(deviceKey, 0)
        } catch (_: Exception) {
            0
        }
    }

    fun setDeviceGainOffsetTenths(deviceKey: String, tenths: Int) {
        val json = try {
            JSONObject(prefs.getString(DolbyConstants.PREF_DEVICE_GAIN_OFFSETS, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        if (tenths == 0) json.remove(deviceKey) else json.put(deviceKey, tenths)
        prefs.edit().putString(DolbyConstants.PREF_DEVICE_GAIN_OFFSETS, json.toString()).apply()
    }

    /** Apply soft clip: scale all gains down if peak would exceed max. */
    fun applySoftClipIfNeeded(gains: IntArray): IntArray {
        if (!isSoftClipEnabled()) {
            return gains.map { clampEqGain(it) }.toIntArray()
        }
        val max = getEqGainMax()
        val peak = gains.maxOfOrNull { abs(it) } ?: 0
        if (peak <= max) return gains.map { clampEqGain(it) }.toIntArray()
        val scale = max.toFloat() / peak.toFloat()
        return gains.map { clampEqGain((it * scale).roundToInt()) }.toIntArray()
    }

    /** Attenuate low bands for high-pass effect (bands 0-3 of 20-band GEQ). */
    fun applyHighPassIfNeeded(gains: IntArray): IntArray {
        if (!isHighPassEnabled() || gains.size < 4) return gains
        val hz = getHighPassHz()
        val cutStrength = when {
            hz >= 80 -> 0.85f
            hz >= 60 -> 0.65f
            hz >= 40 -> 0.45f
            else -> 0.25f
        }
        return gains.mapIndexed { index, gain ->
            if (index <= 3) (gain * (1f - cutStrength)).roundToInt() else gain
        }.toIntArray()
    }

    fun maxGainDb(): Float = getEqGainMax() / 10f

    enum class EqRangePreset(val key: String, val maxRaw: Int, val labelResSuffix: String) {
        STANDARD("standard", DolbyConstants.EQ_GAIN_STANDARD, "eq_range_standard"),
        EXTENDED("extended", DolbyConstants.EQ_GAIN_EXTENDED, "eq_range_extended"),
        EXTREME("extreme", DolbyConstants.EQ_GAIN_EXTREME, "eq_range_extreme");

        companion object {
            fun fromKey(key: String?): EqRangePreset =
                entries.find { it.key == key } ?: STANDARD
        }
    }
}

data class HeadroomInfo(
    val peakEqDb: Float,
    val outputBoostDb: Float,
    val deviceOffsetDb: Float,
    val volmaxContributionDb: Float,
    val totalUsedDb: Float,
    val maxAllowedDb: Float,
    val remainingDb: Float,
    val isOverLimit: Boolean
)

object HeadroomCalculator {

    fun calculate(
        eqGains: IntArray,
        outputBoostTenths: Int,
        outputBoostEnabled: Boolean,
        volmaxEnabled: Boolean,
        volmaxValue: Int,
        deviceOffsetTenths: Int,
        engine: AudioEnginePreferences
    ): HeadroomInfo {
        val peakRaw = eqGains.maxOfOrNull { abs(it) } ?: 0
        val peakEqDb = peakRaw / 10f
        val boostDb = if (outputBoostEnabled) outputBoostTenths / 10f else 0f
        val deviceDb = deviceOffsetTenths / 10f
        val volmaxDb = if (volmaxEnabled) volmaxValue / 16f else 0f
        val total = peakEqDb + abs(boostDb) + abs(deviceDb) + volmaxDb * 0.5f
        val maxAllowed = engine.maxGainDb() + engine.getOutputBoostMaxTenths() / 10f
        return HeadroomInfo(
            peakEqDb = peakEqDb,
            outputBoostDb = boostDb,
            deviceOffsetDb = deviceDb,
            volmaxContributionDb = volmaxDb,
            totalUsedDb = total,
            maxAllowedDb = maxAllowed,
            remainingDb = maxAllowed - total,
            isOverLimit = total > maxAllowed
        )
    }
}
