/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import org.lunaris.dolby.data.DolbyAutomationCoordinator

/**
 * Handles system broadcasts that must remain exported without a custom permission.
 */
class DolbySystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                DolbyAutomationCoordinator.applyBatterySaverIfNeeded(context)
            }
            "android.app.action.INTERRUPTION_FILTER_CHANGED" -> {
                val nm = context.getSystemService(android.app.NotificationManager::class.java)
                val fromExtra = intent.getIntExtra("android.app.extra.INTERRUPTION_FILTER_TYPE", -1)
                val filter = if (fromExtra > 0) fromExtra else nm?.currentInterruptionFilter ?: -1
                val focusActive = DolbyAutomationCoordinator.isFocusInterruptionFilter(filter)
                DolbyAutomationCoordinator.applyFocusModeProfile(context, focusActive)
            }
        }
    }
}
