/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.data.ScheduledProfileManager

class ScheduledProfileReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CHECK) return
        val manager = ScheduledProfileManager(context)
        if (!manager.isEnabled()) return
        try {
            DolbyRepository(context).use { repository ->
                manager.applyActiveRuleIfNeeded(repository)
            }
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Failed to apply scheduled profile: ${e.message}")
        } finally {
            try {
                manager.scheduleNextCheck()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Failed to reschedule profile check: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "ScheduledProfileRx"
        const val ACTION_CHECK = "org.lunaris.dolby.action.CHECK_SCHEDULED_PROFILE"
    }
}
