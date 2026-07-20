/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.ComponentName
import android.content.Context
import android.appwidget.AppWidgetManager
import android.service.quicksettings.TileService
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.provider.SummaryProvider
import org.lunaris.dolby.tile.DolbyTileService
import org.lunaris.dolby.widget.DolbyWidgetProvider

object DolbyStatusNotifier {

    fun notifyChanged(context: Context) {
        refreshWidgets(context)
        refreshTile(context)
        notifySummaryChanged(context)
    }

    private fun refreshWidgets(context: Context) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, DolbyWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { id -> DolbyWidgetProvider.updateWidget(context, manager, id) }
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Failed to refresh widgets: ${e.message}")
        }
    }

    private fun refreshTile(context: Context) {
        try {
            TileService.requestListeningState(
                context,
                ComponentName(context, DolbyTileService::class.java)
            )
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Failed to refresh QS tile: ${e.message}")
        }
    }

    private fun notifySummaryChanged(context: Context) {
        try {
            context.contentResolver.notifyChange(SummaryProvider.CONTENT_URI, null)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Failed to notify summary change: ${e.message}")
        }
    }

    private const val TAG = "DolbyStatusNotifier"
}
