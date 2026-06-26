/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import org.lunaris.dolby.domain.models.ProfileChangeSource
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.domain.models.ScheduledProfileRule
import org.lunaris.dolby.receiver.ScheduledProfileReceiver
import java.util.Calendar
import java.util.UUID

class ScheduledProfileManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(DolbyConstants.PREF_SCHEDULED_PROFILES_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(DolbyConstants.PREF_SCHEDULED_PROFILES_ENABLED, enabled).apply()
        if (enabled) scheduleNextCheck() else cancelScheduledCheck()
    }

    fun getRules(): List<ScheduledProfileRule> {
        val raw = prefs.getString(DolbyConstants.PREF_SCHEDULED_PROFILES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    add(parseRule(array.getJSONObject(i)))
                }
            }
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Failed to parse scheduled rules: ${e.message}")
            emptyList()
        }
    }

    fun saveRules(rules: List<ScheduledProfileRule>) {
        val array = JSONArray()
        rules.forEach { array.put(encodeRule(it)) }
        prefs.edit().putString(DolbyConstants.PREF_SCHEDULED_PROFILES, array.toString()).apply()
        if (isEnabled()) scheduleNextCheck()
    }

    fun addRule(rule: ScheduledProfileRule) {
        saveRules(getRules() + rule)
    }

    fun deleteRule(id: String) {
        saveRules(getRules().filterNot { it.id == id })
    }

    fun findActiveRule(now: Calendar = Calendar.getInstance()): ScheduledProfileRule? {
        val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        return getRules()
            .filter { it.enabled && it.matchesDay(dayOfWeek) && it.containsMinute(minuteOfDay) }
            .maxByOrNull { it.priority }
    }

    fun applyActiveRuleIfNeeded(repository: DolbyRepository) {
        if (!isEnabled()) return
        val rule = findActiveRule() ?: return
        if (repository.getCurrentProfile() != rule.profileId) {
            ProfileChangeHistoryManager(context).recordChange(
                rule.profileId,
                ProfileChangeSource.SCHEDULE,
                rule.name
            )
            repository.setCurrentProfile(rule.profileId)
            DolbyConstants.dlog(TAG, "Applied scheduled profile ${rule.profileId} (${rule.name})")
        }
    }

    fun scheduleNextCheck() {
        if (!isEnabled()) return
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ScheduledProfileReceiver::class.java).apply {
            action = ScheduledProfileReceiver.ACTION_CHECK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + CHECK_INTERVAL_MS
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancelScheduledCheck() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ScheduledProfileReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun encodeRule(rule: ScheduledProfileRule): JSONObject {
        return JSONObject().apply {
            put("id", rule.id)
            put("name", rule.name)
            put("profileId", rule.profileId)
            put("startHour", rule.startHour)
            put("startMinute", rule.startMinute)
            put("endHour", rule.endHour)
            put("endMinute", rule.endMinute)
            put("enabled", rule.enabled)
            put("priority", rule.priority)
            val daysArray = JSONArray()
            rule.daysOfWeek.sorted().forEach { daysArray.put(it) }
            put("daysOfWeek", daysArray)
        }
    }

    private fun parseRule(json: JSONObject): ScheduledProfileRule {
        val days = mutableSetOf<Int>()
        json.optJSONArray("daysOfWeek")?.let { array ->
            for (i in 0 until array.length()) {
                days.add(array.getInt(i))
            }
        }
        return ScheduledProfileRule(
            id = json.getString("id"),
            name = json.getString("name"),
            profileId = json.getInt("profileId"),
            startHour = json.getInt("startHour"),
            startMinute = json.getInt("startMinute"),
            endHour = json.getInt("endHour"),
            endMinute = json.getInt("endMinute"),
            enabled = json.optBoolean("enabled", true),
            daysOfWeek = days,
            priority = json.optInt("priority", 0)
        )
    }

    companion object {
        private const val TAG = "ScheduledProfileManager"
        private const val REQUEST_CODE = 42001
        const val CHECK_INTERVAL_MS = 60_000L

        fun newRuleId(): String = UUID.randomUUID().toString()
    }
}
