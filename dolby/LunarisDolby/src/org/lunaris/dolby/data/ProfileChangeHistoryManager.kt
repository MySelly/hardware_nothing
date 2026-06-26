/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.domain.models.ProfileChangeSource
import org.lunaris.dolby.domain.models.ProfileHistoryEntry

class ProfileChangeHistoryManager(context: Context) {

    private val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)

    fun recordChange(profileId: Int, source: ProfileChangeSource, detail: String) {
        val history = getHistory().toMutableList()
        history.add(0, ProfileHistoryEntry(
            timestamp = System.currentTimeMillis(),
            profileId = profileId,
            source = source,
            detail = detail
        ))
        while (history.size > DolbyConstants.MAX_HISTORY_ENTRIES) {
            history.removeAt(history.lastIndex)
        }
        saveHistory(history)
        prefs.edit()
            .putString(DolbyConstants.PREF_LAST_AUTOMATION_SOURCE, source.key)
            .putString(DolbyConstants.PREF_LAST_AUTOMATION_DETAIL, detail)
            .apply()
    }

    fun getHistory(): List<ProfileHistoryEntry> {
        val raw = prefs.getString(DolbyConstants.PREF_PROFILE_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    add(parseEntry(array.getJSONObject(i)))
                }
            }
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Failed to parse history: ${e.message}")
            emptyList()
        }
    }

    fun clearHistory() {
        prefs.edit().remove(DolbyConstants.PREF_PROFILE_HISTORY).apply()
    }

    fun saveUndoProfile(profileId: Int) {
        prefs.edit()
            .putInt(DolbyConstants.PREF_UNDO_PROFILE, profileId)
            .putLong(DolbyConstants.PREF_UNDO_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun consumeUndoProfile(): Int? {
        val ts = prefs.getLong(DolbyConstants.PREF_UNDO_TIMESTAMP, 0L)
        if (ts == 0L || System.currentTimeMillis() - ts > DolbyConstants.UNDO_WINDOW_MS) {
            return null
        }
        val profile = prefs.getInt(DolbyConstants.PREF_UNDO_PROFILE, -1)
        prefs.edit()
            .remove(DolbyConstants.PREF_UNDO_PROFILE)
            .remove(DolbyConstants.PREF_UNDO_TIMESTAMP)
            .apply()
        return profile.takeIf { it >= 0 }
    }

    private fun saveHistory(entries: List<ProfileHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("timestamp", entry.timestamp)
                put("profileId", entry.profileId)
                put("source", entry.source.key)
                put("detail", entry.detail)
            })
        }
        prefs.edit().putString(DolbyConstants.PREF_PROFILE_HISTORY, array.toString()).apply()
    }

    private fun parseEntry(json: JSONObject): ProfileHistoryEntry {
        return ProfileHistoryEntry(
            timestamp = json.getLong("timestamp"),
            profileId = json.getInt("profileId"),
            source = ProfileChangeSource.entries.find { it.key == json.getString("source") }
                ?: ProfileChangeSource.MANUAL,
            detail = json.getString("detail")
        )
    }

    companion object {
        private const val TAG = "ProfileHistory"
    }
}
