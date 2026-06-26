/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.domain.models.SleepTimerAction
import org.lunaris.dolby.domain.models.SleepTimerConfig
import org.lunaris.dolby.receiver.SleepTimerReceiver

class SleepTimerManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)

    fun getActiveTimer(): SleepTimerConfig? {
        val endMs = prefs.getLong(DolbyConstants.PREF_SLEEP_TIMER_END_MS, 0L)
        if (endMs <= System.currentTimeMillis()) {
            if (endMs > 0) clearTimer()
            return null
        }
        val actionKey = prefs.getString(DolbyConstants.PREF_SLEEP_TIMER_ACTION, null) ?: return null
        val action = when (actionKey) {
            DolbyConstants.SLEEP_ACTION_DISABLE -> SleepTimerAction.DISABLE
            DolbyConstants.SLEEP_ACTION_PROFILE -> SleepTimerAction.SWITCH_PROFILE
            else -> SleepTimerAction.RESTORE_PROFILE
        }
        return SleepTimerConfig(
            endTimeMs = endMs,
            action = action,
            targetProfileId = prefs.getInt(DolbyConstants.PREF_SLEEP_TIMER_PROFILE, 0),
            previousProfileId = prefs.getInt("sleep_timer_prev_profile", 0)
        )
    }

    fun startTimer(
        durationMinutes: Int,
        action: SleepTimerAction,
        targetProfileId: Int,
        previousProfileId: Int
    ) {
        val endMs = System.currentTimeMillis() + durationMinutes * 60_000L
        val actionKey = when (action) {
            SleepTimerAction.DISABLE -> DolbyConstants.SLEEP_ACTION_DISABLE
            SleepTimerAction.SWITCH_PROFILE -> DolbyConstants.SLEEP_ACTION_PROFILE
            SleepTimerAction.RESTORE_PROFILE -> "restore"
        }
        prefs.edit()
            .putLong(DolbyConstants.PREF_SLEEP_TIMER_END_MS, endMs)
            .putString(DolbyConstants.PREF_SLEEP_TIMER_ACTION, actionKey)
            .putInt(DolbyConstants.PREF_SLEEP_TIMER_PROFILE, targetProfileId)
            .putInt("sleep_timer_prev_profile", previousProfileId)
            .apply()
        scheduleAlarm(endMs)
    }

    fun clearTimer() {
        prefs.edit()
            .remove(DolbyConstants.PREF_SLEEP_TIMER_END_MS)
            .remove(DolbyConstants.PREF_SLEEP_TIMER_ACTION)
            .remove(DolbyConstants.PREF_SLEEP_TIMER_PROFILE)
            .remove("sleep_timer_prev_profile")
            .apply()
        cancelAlarm()
    }

    private fun scheduleAlarm(triggerAtMs: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, SleepTimerReceiver::class.java).apply {
            action = SleepTimerReceiver.ACTION_FIRE
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
    }

    private fun cancelAlarm() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, SleepTimerReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }

    companion object {
        private const val TAG = "SleepTimerManager"
        private const val REQUEST_CODE = 42002
    }
}
