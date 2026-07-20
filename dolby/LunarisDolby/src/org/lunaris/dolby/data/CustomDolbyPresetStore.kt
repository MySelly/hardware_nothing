/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.R
import org.lunaris.dolby.domain.models.BandGain
import org.lunaris.dolby.domain.models.BandMode
import org.lunaris.dolby.domain.models.CustomDolbyPresetSummary
import org.lunaris.dolby.domain.models.EqualizerPreset

/**
 * Custom Dolby preset persistence and full-backup JSON, extracted from [DolbyRepository].
 *
 * Effect / profile application stays on the repository via [Host] callbacks so this
 * store remains SharedPreferences + JSON oriented.
 */
class CustomDolbyPresetStore(
    private val context: Context,
    private val host: Host
) {

    interface Host {
        fun getCurrentProfile(): Int
        fun getBandMode(): BandMode
        fun setBandMode(mode: BandMode)
        fun getDolbyEnabled(): Boolean
        fun setDolbyEnabled(enabled: Boolean)
        fun setCurrentProfile(profile: Int)
        fun getProfilePrefs(profile: Int): SharedPreferences
        /** Push GEQ + DAP settings after prefs were restored for [profile]. */
        fun applyRestoredProfile(profile: Int)
        fun getUserPresets(): List<EqualizerPreset>
        fun clearAllUserPresets()
        fun addUserPreset(name: String, bandGains: List<BandGain>, bandMode: BandMode)
    }

    private val customPresetsPrefs = context.getSharedPreferences(
        DolbyConstants.PREF_FILE_CUSTOM_PRESETS,
        Context.MODE_PRIVATE
    )

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

        val profile = host.getCurrentProfile()
        val snapshot = JSONObject().apply {
            put("savedFromProfile", profile)
            put("bandMode", host.getBandMode().value)
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
        val profile = host.getCurrentProfile()
        val settings = json.getJSONObject("settings")
        restoreProfilePrefs(profile, settings)
        host.setBandMode(BandMode.fromValue(json.getString("bandMode")))
        host.applyRestoredProfile(profile)
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
            put(DolbyConstants.PREF_ENABLE, host.getDolbyEnabled())
            put(DolbyConstants.PREF_PROFILE, host.getCurrentProfile())
            put(DolbyConstants.PREF_BAND_MODE, host.getBandMode().value)
        }

        val profiles = JSONArray()
        profileIds.forEach { profileId ->
            profiles.put(exportProfilePrefsJson(profileId))
        }

        val userPresets = JSONArray()
        host.getUserPresets().forEach { preset ->
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
        host.setBandMode(bandMode)

        val profiles = json.getJSONArray("profiles")
        for (i in 0 until profiles.length()) {
            val profileJson = profiles.getJSONObject(i)
            val profileId = profileJson.getInt("id")
            restoreProfilePrefs(profileId, profileJson)
            host.applyRestoredProfile(profileId)
        }

        if (json.has("user_presets")) {
            host.clearAllUserPresets()
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
                host.addUserPreset(name, bandGains, presetBandMode)
            }
        }

        val targetProfile = global.getInt(DolbyConstants.PREF_PROFILE)
        host.setCurrentProfile(targetProfile)
        host.setDolbyEnabled(global.getBoolean(DolbyConstants.PREF_ENABLE))
    }

    fun exportProfilePrefsJson(profile: Int): JSONObject {
        val prefs = host.getProfilePrefs(profile)
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

    fun restoreProfilePrefs(profile: Int, json: JSONObject) {
        val editor = host.getProfilePrefs(profile).edit()
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

    companion object {
        private const val TAG = "CustomDolbyPresetStore"
        private const val BACKUP_TYPE = "dolby_full_backup"
        private const val BACKUP_VERSION = 1
    }
}
