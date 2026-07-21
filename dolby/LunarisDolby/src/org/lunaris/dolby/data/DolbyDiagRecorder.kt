/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight in-app diagnostics ring buffer.
 *
 * Lets users copy/share a short text report instead of uploading a full logcat file.
 */
object DolbyDiagRecorder {

    private const val TAG = "DolbyDiag"
    private const val PREFS = "dolby_diag"
    private const val KEY_EVENTS = "events_json"
    private const val KEY_LAST_CRASH = "last_crash"
    private const val KEY_LAST_EFFECT_ERROR = "last_effect_error"
    private const val MAX_EVENTS = 40
    const val CRASH_FILE_NAME = "dolby_last_crash.txt"

    private val memoryEvents = ArrayDeque<String>(MAX_EVENTS)
    private val lock = Any()

    fun record(context: Context, tag: String, message: String, error: Throwable? = null) {
        val line = formatLine(tag, message, error)
        Log.e(TAG, line, error)
        synchronized(lock) {
            if (memoryEvents.size >= MAX_EVENTS) memoryEvents.removeFirst()
            memoryEvents.addLast(line)
            persistEvents(context.applicationContext)
            if (tag.contains("effect", ignoreCase = true) || tag.contains("hal", ignoreCase = true)) {
                context.applicationContext
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_EFFECT_ERROR, line)
                    .apply()
            }
        }
    }

    fun recordCrash(context: Context, threadName: String, throwable: Throwable) {
        val stamp = timestamp()
        val text = buildString {
            appendLine("time=$stamp")
            appendLine("thread=$threadName")
            appendLine("exception=${throwable.javaClass.name}: ${throwable.message}")
            appendLine(throwable.stackTraceToString().take(4000))
        }
        Log.e(TAG, "Uncaught crash\n$text", throwable)
        try {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_CRASH, text)
                .apply()
            writeCrashFile(context.applicationContext, text)
            record(context, "crash", "${throwable.javaClass.simpleName}: ${throwable.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store crash text", e)
        }
    }

    fun lastCrash(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CRASH, null)
            ?: readCrashFile(context.applicationContext)
    }

    fun lastEffectError(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_EFFECT_ERROR, null)
    }

    fun recentEvents(context: Context): List<String> {
        synchronized(lock) {
            if (memoryEvents.isNotEmpty()) return memoryEvents.toList()
            return loadPersistedEvents(context.applicationContext)
        }
    }

    fun clearCrash(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_CRASH)
            .apply()
        try {
            crashFile(context.applicationContext).delete()
        } catch (_: Exception) {
        }
    }

    fun buildShareableReport(
        context: Context,
        dolbyEnabled: Boolean,
        currentProfile: Int,
        profileName: String,
        effectHasControl: Boolean,
        effectCreateError: String?,
        activeDevice: String,
        activeSource: String,
        activeDetail: String
    ): String {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = pkg.versionName ?: "?"
        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkg.longVersionCode
        } else {
            pkg.versionCode.toLong()
        }
        return buildString {
            appendLine("=== LunarisDolby diagnostics ===")
            appendLine("time=${timestamp()}")
            appendLine("pkg=${context.packageName}")
            appendLine("version=$versionName ($versionCode)")
            appendLine("sdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine("Dolby: ${if (dolbyEnabled) "ON" else "OFF"}")
            appendLine("Profile: $profileName ($currentProfile)")
            appendLine("Effect control: ${if (effectHasControl) "OK" else "FAIL"}")
            if (!effectCreateError.isNullOrBlank()) {
                appendLine("Effect create error: $effectCreateError")
            }
            appendLine("Device: $activeDevice")
            appendLine("Source: $activeSource")
            appendLine("Detail: $activeDetail")
            appendLine()
            val crash = lastCrash(context)
            if (!crash.isNullOrBlank()) {
                appendLine("--- last crash ---")
                appendLine(crash.trim())
                appendLine()
            }
            val effectErr = lastEffectError(context)
            if (!effectErr.isNullOrBlank()) {
                appendLine("--- last effect error ---")
                appendLine(effectErr)
                appendLine()
            }
            val events = recentEvents(context)
            if (events.isNotEmpty()) {
                appendLine("--- recent events ---")
                events.takeLast(20).forEach { appendLine(it) }
            }
            appendLine("=== end ===")
        }
    }

    private fun formatLine(tag: String, message: String, error: Throwable?): String {
        val err = error?.let { " | ${it.javaClass.simpleName}: ${it.message}" }.orEmpty()
        return "${timestamp()} [$tag] $message$err"
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun persistEvents(context: Context) {
        val arr = JSONArray()
        memoryEvents.forEach { arr.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EVENTS, arr.toString())
            .apply()
    }

    private fun loadPersistedEvents(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EVENTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(arr.getString(i))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun crashFile(context: Context): File = File(context.filesDir, CRASH_FILE_NAME)

    private fun writeCrashFile(context: Context, text: String) {
        crashFile(context).writeText(text)
    }

    private fun readCrashFile(context: Context): String? {
        val file = crashFile(context)
        return if (file.exists()) file.readText() else null
    }
}
