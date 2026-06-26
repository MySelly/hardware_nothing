/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import android.media.AudioDeviceInfo
import org.json.JSONArray
import org.json.JSONObject
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.domain.models.BluetoothProfileRule

class BluetoothProfileManager(context: Context) {

    private val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
    private val deviceStateManager = DeviceStateManager(context)

    fun getRules(): List<BluetoothProfileRule> {
        val raw = prefs.getString(DolbyConstants.PREF_BT_PROFILE_RULES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val json = array.getJSONObject(i)
                    add(BluetoothProfileRule(
                        deviceKey = json.getString("deviceKey"),
                        displayName = json.getString("displayName"),
                        profileId = json.getInt("profileId"),
                        enabled = json.optBoolean("enabled", true)
                    ))
                }
            }
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Failed to parse BT rules: ${e.message}")
            emptyList()
        }
    }

    fun saveRules(rules: List<BluetoothProfileRule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(JSONObject().apply {
                put("deviceKey", rule.deviceKey)
                put("displayName", rule.displayName)
                put("profileId", rule.profileId)
                put("enabled", rule.enabled)
            })
        }
        prefs.edit().putString(DolbyConstants.PREF_BT_PROFILE_RULES, array.toString()).apply()
    }

    fun addRule(device: AudioDeviceInfo, profileId: Int) {
        val key = deviceStateManager.deviceKey(device)
        val name = deviceStateManager.deviceDisplayName(device)
        val updated = getRules()
            .filterNot { it.deviceKey == key } +
            BluetoothProfileRule(key, name, profileId)
        saveRules(updated)
    }

    fun deleteRule(deviceKey: String) {
        saveRules(getRules().filterNot { it.deviceKey == deviceKey })
    }

    fun findRuleForDevice(device: AudioDeviceInfo?): BluetoothProfileRule? {
        if (device == null) return null
        val key = deviceStateManager.deviceKey(device)
        return getRules().firstOrNull { it.deviceKey == key && it.enabled }
    }

    companion object {
        private const val TAG = "BluetoothProfileMgr"
    }
}
