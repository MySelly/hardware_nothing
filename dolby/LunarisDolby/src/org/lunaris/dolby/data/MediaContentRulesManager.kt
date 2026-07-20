/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import org.json.JSONObject
import org.lunaris.dolby.DolbyConstants

class MediaContentRulesManager(context: Context) {

    private val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)

    fun getProfileForContentType(contentType: String): Int {
        val key = profilePrefKey(contentType) ?: return -1
        return prefs.getInt(key, defaultProfileFor(contentType))
    }

    fun setProfileForContentType(contentType: String, profileId: Int) {
        val key = profilePrefKey(contentType) ?: return
        prefs.edit().putInt(key, profileId).apply()
    }

    fun detectContentType(packageName: String): String? {
        val overrides = getPackageOverrides()
        if (overrides.has(packageName)) {
            return overrides.optString(packageName).takeIf { it.isNotBlank() }
        }
        return DEFAULT_PACKAGE_TYPES[packageName]
    }

    fun setPackageContentType(packageName: String, contentType: String?) {
        val json = getPackageOverrides()
        if (contentType.isNullOrBlank()) {
            json.remove(packageName)
        } else {
            json.put(packageName, contentType)
        }
        prefs.edit().putString(DolbyConstants.PREF_MEDIA_PACKAGE_OVERRIDES, json.toString()).apply()
    }

    fun getPackageOverridesMap(): Map<String, String> {
        val json = getPackageOverrides()
        return buildMap {
            json.keys().forEach { key ->
                put(key, json.getString(key))
            }
        }
    }

    private fun getPackageOverrides(): JSONObject {
        val raw = prefs.getString(DolbyConstants.PREF_MEDIA_PACKAGE_OVERRIDES, null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun profilePrefKey(contentType: String): String? = when (contentType.lowercase()) {
        "music" -> DolbyConstants.PREF_MEDIA_PROFILE_MUSIC
        "video", "movie" -> DolbyConstants.PREF_MEDIA_PROFILE_VIDEO
        "game" -> DolbyConstants.PREF_MEDIA_PROFILE_GAME
        "speech", "podcast" -> DolbyConstants.PREF_MEDIA_PROFILE_SPEECH
        else -> null
    }

    companion object {
        val CONTENT_TYPES = listOf("music", "video", "game", "speech")

        /** Default Dolby profile id for a media content type (no Context required). */
        fun defaultProfileFor(contentType: String): Int = when (contentType.lowercase()) {
            "music" -> 2
            "video", "movie" -> 1
            "game" -> 3
            "speech", "podcast" -> 4
            else -> -1
        }

        val DEFAULT_PACKAGE_TYPES = mapOf(
            "com.spotify.music" to "music",
            "com.google.android.apps.youtube.music" to "music",
            "com.apple.android.music" to "music",
            "com.amazon.mp3" to "music",
            "com.google.android.youtube" to "video",
            "com.netflix.mediaclient" to "video",
            "com.disney.disneyplus" to "video",
            "tv.twitch.android.app" to "video",
            "com.mojang.minecraftpe" to "game",
            "com.epicgames.fortnite" to "game",
            "com.activision.callofduty.shooter" to "game",
            "com.google.android.apps.podcasts" to "speech",
            "com.audible.application" to "speech"
        )
    }
}
