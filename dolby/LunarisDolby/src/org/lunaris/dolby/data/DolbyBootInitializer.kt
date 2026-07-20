/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import org.lunaris.dolby.service.AppProfileMonitorService
import org.lunaris.dolby.service.DolbyEffectService
import org.lunaris.dolby.service.DolbyNotificationListener

/**
 * Central boot / cold-start initializer for Dolby runtime services.
 *
 * Call order (keep stable):
 * 1. Start [DolbyEffectService] so the audio effect is bound early.
 * 2. Start app-profile monitoring when the user has enabled it.
 * 3. Rebind the notification listener if the user has granted access.
 * 4. Reschedule profile alarms when scheduled profiles are enabled.
 *
 * [org.lunaris.dolby.BootCompletedReceiver] is the primary caller after boot.
 * [DolbyNotificationListener] may call [startAppProfileMonitoringIfEnabled] only
 * (steps 2) when it connects later — it must not re-run the full boot sequence,
 * which would duplicate effect-service starts and schedule churn.
 */
object DolbyBootInitializer {

    private const val TAG = "Dolby-BootInit"
    private const val PREF_APP_PROFILE_MONITORING = "app_profile_monitoring_enabled"

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        try {
            // 1) Effect service first — profile apply paths depend on it.
            DolbyEffectService.start(appContext)

            // 2) App profile monitor (idempotent start).
            startAppProfileMonitoringIfEnabled(appContext)

            // 3) Rebind NL if already enabled in Settings.
            if (isNotificationListenerEnabled(appContext)) {
                requestNotificationListenerRebind(appContext)
            }

            // 4) Schedule next profile check when schedules are on.
            ScheduledProfileManager(appContext).let { manager ->
                if (manager.isEnabled()) manager.scheduleNextCheck()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Dolby boot path", e)
        }
    }

    /** Step 2 only — safe to call from notification listener onCreate/connected. */
    fun startAppProfileMonitoringIfEnabled(context: Context) {
        val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_APP_PROFILE_MONITORING, false)) {
            AppProfileMonitorService.startMonitoring(context)
        }
    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val cn = ComponentName(context, DolbyNotificationListener::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(cn.flattenToString()) == true
    }

    private fun requestNotificationListenerRebind(context: Context) {
        try {
            val cn = ComponentName(context, DolbyNotificationListener::class.java)
            DolbyNotificationListener::class.java.getMethod(
                "requestRebind",
                ComponentName::class.java
            ).invoke(null, cn)
            Log.d(TAG, "Requested notification listener rebind")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request notification listener rebind", e)
        }
    }
}
