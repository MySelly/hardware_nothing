/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.lunaris.dolby.R
import org.lunaris.dolby.data.AutomationStatusResolver
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.data.ProfileChangeHistoryManager
import org.lunaris.dolby.data.SleepTimerManager
import org.lunaris.dolby.domain.models.ProfileChangeSource
import org.lunaris.dolby.domain.models.SleepTimerAction
import org.lunaris.dolby.service.DolbyEffectService
import org.lunaris.dolby.ui.DolbyActivity

class DolbyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                val repository = DolbyRepository(context)
                try {
                    val enabled = !repository.getDolbyEnabled()
                    repository.setDolbyEnabled(enabled)
                    ProfileChangeHistoryManager(context).recordChange(
                        repository.getCurrentProfile(),
                        ProfileChangeSource.WIDGET,
                        if (enabled) "Enabled" else "Disabled"
                    )
                    if (enabled) DolbyEffectService.start(context) else DolbyEffectService.stop(context)
                } finally {
                    repository.close()
                }
                refreshAll(context)
            }
            ACTION_CYCLE -> {
                val repository = DolbyRepository(context)
                try {
                    if (!repository.getDolbyEnabled()) {
                        repository.setDolbyEnabled(true)
                        DolbyEffectService.start(context)
                    }
                    val next = repository.cycleToNextProfile()
                    ProfileChangeHistoryManager(context).recordChange(
                        next,
                        ProfileChangeSource.WIDGET,
                        repository.getProfileDisplayName(next)
                    )
                } finally {
                    repository.close()
                }
                refreshAll(context)
            }
            ACTION_SLEEP -> {
                val timerManager = SleepTimerManager(context)
                val active = timerManager.getActiveTimer()
                if (active != null) {
                    timerManager.clearTimer()
                } else {
                    val repository = DolbyRepository(context)
                    try {
                        val current = repository.getCurrentProfile()
                        timerManager.startTimer(30, SleepTimerAction.RESTORE_PROFILE, 0, current)
                    } finally {
                        repository.close()
                    }
                }
                refreshAll(context)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "org.lunaris.dolby.widget.TOGGLE"
        const val ACTION_CYCLE = "org.lunaris.dolby.widget.CYCLE"
        const val ACTION_SLEEP = "org.lunaris.dolby.widget.SLEEP"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DolbyWidgetProvider::class.java))
            ids.forEach { id -> updateWidget(context, manager, id) }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val repository = DolbyRepository(context)
            val statusResolver = AutomationStatusResolver(context)
            try {
                val enabled = repository.getDolbyEnabled()
                val profileName = repository.getProfileDisplayName(repository.getCurrentProfile())
                val status = statusResolver.resolve()
                val timer = SleepTimerManager(context).getActiveTimer()
                val views = RemoteViews(context.packageName, R.layout.widget_dolby)
                views.setTextViewText(R.id.widget_title, context.getString(R.string.dolby_title))
                views.setTextViewText(
                    R.id.widget_subtitle,
                    if (enabled) context.getString(R.string.dolby_on_with_profile, profileName)
                    else context.getString(R.string.dolby_off)
                )
                val automationText = buildString {
                    status.activeSource?.let { append(it.key) }
                    if (status.activeDetail.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(status.activeDetail)
                    }
                    timer?.let {
                        if (isNotEmpty()) append(" · ")
                        val mins = ((it.endTimeMs - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
                        append(context.getString(R.string.widget_sleep_remaining, mins))
                    }
                }
                if (automationText.isBlank()) {
                    views.setViewVisibility(R.id.widget_automation, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_automation, View.VISIBLE)
                    views.setTextViewText(R.id.widget_automation, automationText)
                }
                views.setTextViewText(
                    R.id.widget_toggle,
                    if (enabled) context.getString(R.string.dolby_off) else context.getString(R.string.dolby_enable)
                )
                views.setTextViewText(
                    R.id.widget_sleep,
                    if (timer != null) context.getString(R.string.widget_sleep_cancel)
                    else context.getString(R.string.widget_sleep_timer)
                )

                val openIntent = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, DolbyActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, openIntent)
                views.setOnClickPendingIntent(
                    R.id.widget_toggle,
                    broadcast(context, ACTION_TOGGLE, 1)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_cycle,
                    broadcast(context, ACTION_CYCLE, 2)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_sleep,
                    broadcast(context, ACTION_SLEEP, 3)
                )
                manager.updateAppWidget(widgetId, views)
            } finally {
                statusResolver.close()
                repository.close()
            }
        }

        private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, DolbyWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
