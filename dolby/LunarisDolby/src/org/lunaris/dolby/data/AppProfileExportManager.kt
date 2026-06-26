/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.lunaris.dolby.DolbyConstants

class AppProfileExportManager(context: Context) {

    private val appProfileManager = AppProfileManager(context)
    private val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)

    fun exportToJson(): String {
        val mappings = appProfileManager.getAppsWithProfiles()
        val array = JSONArray()
        mappings.forEach { (pkg, profile) ->
            array.put(JSONObject().apply {
                put("package", pkg)
                put("profile", profile)
            })
        }
        return JSONObject().apply {
            put("version", 1)
            put("mappings", array)
        }.toString(2)
    }

    fun importFromJson(json: String): Int {
        val root = JSONObject(json)
        val array = root.getJSONArray("mappings")
        var count = 0
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val pkg = item.getString("package")
            val profile = item.getInt("profile")
            appProfileManager.setAppProfile(pkg, profile)
            count++
        }
        prefs.edit().putLong(DolbyConstants.PREF_APP_PROFILES_EXPORT, System.currentTimeMillis()).apply()
        return count
    }
}
