/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.domain.models.BandGain
import org.lunaris.dolby.domain.models.BandMode
import org.lunaris.dolby.domain.models.EqualizerPreset

/**
 * EQ user-preset persistence extracted from DolbyRepository.
 */
class EqualizerPresetStore(context: Context) {

    private val presetsPrefs = context.getSharedPreferences(DolbyConstants.PREF_FILE_PRESETS, Context.MODE_PRIVATE)
    private val presetCacheLock = Any()
    private var cachedPresets: List<EqualizerPreset>? = null

    fun getUserPresets(bandMode: BandMode): List<EqualizerPreset> {
        synchronized(presetCacheLock) {
            cachedPresets?.let { return it }

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

    fun addUserPreset(name: String, bandGains: List<BandGain>, bandMode: BandMode) {
        try {
            val gains = EqualizerBandCodec.serializeGains(bandGains, bandMode).joinToString(",")
            val value = "$gains|${bandMode.value}"
            presetsPrefs.edit().putString(name, value).apply()
            invalidateCache()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error adding user preset: ${e.message}")
        }
    }

    fun deleteUserPreset(name: String) {
        try {
            presetsPrefs.edit().remove(name).apply()
            invalidateCache()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error deleting user preset: ${e.message}")
        }
    }

    fun clearAllUserPresets() {
        try {
            presetsPrefs.edit().clear().apply()
            invalidateCache()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error clearing user presets: ${e.message}")
        }
    }

    fun invalidateCache() {
        synchronized(presetCacheLock) {
            cachedPresets = null
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
                    bandGains = EqualizerBandCodec.deserializeGains(gains, presetBandMode),
                    isUserDefined = true,
                    bandMode = presetBandMode
                )
            } else {
                val gains = valueStr.split(",").map { it.toInt() }.toIntArray()
                EqualizerPreset(
                    name = name,
                    bandGains = EqualizerBandCodec.deserializeGains(gains, BandMode.TEN_BAND),
                    isUserDefined = true,
                    bandMode = BandMode.TEN_BAND
                )
            }
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error parsing preset value: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "EqualizerPresetStore"
    }
}
