/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby

import android.app.Application
import android.util.Log
import org.lunaris.dolby.data.DolbyDiagRecorder

/**
 * Captures uncaught crashes so the next launch can surface them in Diagnostics
 * without requiring the user to upload a full logcat dump.
 */
class DolbyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                DolbyDiagRecorder.recordCrash(this, thread.name, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist crash diagnostics", e)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "DolbyApplication"
    }
}
