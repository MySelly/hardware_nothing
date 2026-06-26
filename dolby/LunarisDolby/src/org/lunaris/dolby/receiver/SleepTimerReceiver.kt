/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.lunaris.dolby.data.DolbyAutomationCoordinator

class SleepTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_FIRE) {
            DolbyAutomationCoordinator.handleSleepTimerFire(context)
        }
    }

    companion object {
        const val ACTION_FIRE = "org.lunaris.dolby.action.SLEEP_TIMER_FIRE"
    }
}
