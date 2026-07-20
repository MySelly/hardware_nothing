/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.lunaris.dolby.data.DolbyBootInitializer

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent: ${intent.action}")
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED -> {
                // Full cold-start path: effect service → app monitor → NL rebind → schedules.
                DolbyBootInitializer.initialize(context)
            }
        }
    }

    companion object {
        private const val TAG = "Dolby-Boot"
    }
}
