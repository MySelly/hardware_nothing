/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioDeviceInfo
import org.json.JSONArray
import org.json.JSONObject
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.domain.models.BluetoothProfileRule

data class BondedBluetoothDevice(
    val deviceKey: String,
    val displayName: String,
    val address: String
)

class BluetoothProfileManager(context: Context) {

    private val context = context.applicationContext
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
        addRule(
            deviceKey = deviceStateManager.deviceKey(device),
            displayName = deviceStateManager.deviceDisplayName(device),
            profileId = profileId
        )
    }

    fun addRule(deviceKey: String, displayName: String, profileId: Int) {
        val updated = getRules()
            .filterNot { it.deviceKey == deviceKey } +
            BluetoothProfileRule(deviceKey, displayName, profileId)
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

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BondedBluetoothDevice> {
        return try {
            val manager = context.getSystemService(BluetoothManager::class.java) ?: return emptyList()
            val adapter = manager.adapter ?: return emptyList()
            adapter.bondedDevices
                ?.map { device ->
                    val address = device.address.orEmpty()
                    BondedBluetoothDevice(
                        deviceKey = deviceKeyFromAddress(address),
                        displayName = device.name?.takeIf { it.isNotBlank() } ?: address,
                        address = address
                    )
                }
                ?.sortedBy { it.displayName.lowercase() }
                ?: emptyList()
        } catch (e: SecurityException) {
            DolbyConstants.dlog(TAG, "Missing BLUETOOTH_CONNECT for bonded devices: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Failed to list bonded devices: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "BluetoothProfileMgr"

        fun deviceKeyFromAddress(address: String): String {
            val addr = address.takeIf { it.isNotBlank() } ?: "unknown"
            return "bt_${addr.replace(":", "_")}"
        }
    }
}
