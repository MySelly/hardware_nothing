/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.domain.models.AutomationStatus
import org.lunaris.dolby.domain.models.ProfileChangeSource
import java.util.Calendar

class AutomationStatusResolver(context: Context) {

    private val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
    private val scheduledManager = ScheduledProfileManager(context)
    private val btManager = BluetoothProfileManager(context)
    private val repository = DolbyRepository(context)

    fun resolve(): AutomationStatus {
        return try {
            val now = Calendar.getInstance()
            val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)

            val activeSchedule = scheduledManager.getRules()
                .filter { it.enabled && it.matchesDay(dayOfWeek) && it.containsMinute(minuteOfDay) }
                .maxByOrNull { it.priority }

            val lastApp = prefs.getString("last_foreground_app_package", null)
            val lastAppName = prefs.getString("last_foreground_app_name", null)
            val device = repository.activeAudioDevice.value
            val btRule = btManager.getRules().firstOrNull {
                device.name.contains(it.displayName, ignoreCase = true) && it.enabled
            }
            val mediaType = prefs.getString("last_media_content_type", null)
            val sourceKey = prefs.getString(DolbyConstants.PREF_LAST_AUTOMATION_SOURCE, null)
            val source = ProfileChangeSource.entries.find { it.key == sourceKey }
            val detail = prefs.getString(DolbyConstants.PREF_LAST_AUTOMATION_DETAIL, "") ?: ""

            var effectHealthy = false
            try {
                effectHealthy = repository.hasEffectControl()
            } catch (_: Exception) {
            }

            AutomationStatus(
                activeSource = source,
                activeDetail = detail,
                scheduledRuleName = activeSchedule?.name,
                appPackage = lastAppName ?: lastApp,
                deviceName = device.name,
                bluetoothRuleName = btRule?.displayName,
                mediaContentType = mediaType,
                atmosContentActive = prefs.getBoolean(DolbyConstants.PREF_ATMOS_CONTENT_ACTIVE, false),
                dolbyEffectHealthy = effectHealthy,
                spatialAudioEnabled = prefs.getBoolean(DolbyConstants.PREF_SPATIAL_AUDIO_ENABLED, false)
            )
        } catch (e: Exception) {
            DolbyConstants.dlog("AutomationStatus", "resolve failed: ${e.message}")
            AutomationStatus(
                activeSource = null,
                activeDetail = "",
                scheduledRuleName = null,
                appPackage = null,
                deviceName = null,
                bluetoothRuleName = null,
                mediaContentType = null,
                atmosContentActive = false,
                dolbyEffectHealthy = false,
                spatialAudioEnabled = false
            )
        }
    }

    fun close() {
        repository.close()
    }
}
