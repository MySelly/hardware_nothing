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
import android.widget.RemoteViews
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.R
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.data.ProfileChangeHistoryManager
import org.lunaris.dolby.domain.models.ProfileChangeSource
import org.lunaris.dolby.service.DolbyEffectService
import org.lunaris.dolby.ui.DolbyActivity

class DolbyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
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
            val manager = AppWidgetManager.getInstance(context)
            onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, DolbyWidgetProvider::class.java)))
        }
    }

    companion object {
        const val ACTION_TOGGLE = "org.lunaris.dolby.widget.TOGGLE"

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val repository = DolbyRepository(context)
            try {
                val enabled = repository.getDolbyEnabled()
                val profileName = repository.getProfileDisplayName(repository.getCurrentProfile())
                val views = RemoteViews(context.packageName, R.layout.widget_dolby)
                views.setTextViewText(R.id.widget_title, context.getString(R.string.dolby_title))
                views.setTextViewText(
                    R.id.widget_subtitle,
                    if (enabled) context.getString(R.string.dolby_on_with_profile, profileName)
                    else context.getString(R.string.dolby_off)
                )
                val openIntent = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, DolbyActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val toggleIntent = PendingIntent.getBroadcast(
                    context, 1,
                    Intent(context, DolbyWidgetProvider::class.java).setAction(ACTION_TOGGLE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, openIntent)
                views.setOnClickPendingIntent(R.id.widget_toggle, toggleIntent)
                manager.updateAppWidget(widgetId, views)
            } finally {
                repository.close()
            }
        }
    }
}
