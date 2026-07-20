/*
 * Copyright (C) 2019 The Android Open Source Project
 *           (C) 2023-24 Paranoid Android
 *           (C) 2024-2025 Lunaris AOSP
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.lunaris.dolby.R
import org.lunaris.dolby.data.AutomationStatusResolver
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.domain.models.ProfileChangeSource

private const val KEY_DOLBY = "dolby"
private const val META_DATA_PREFERENCE_SUMMARY = "com.android.settings.summary"

class SummaryProvider : ContentProvider() {

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val summary = when (method) {
            KEY_DOLBY -> getDolbySummary()
            else -> return null
        }
        return Bundle().apply {
            putString(META_DATA_PREFERENCE_SUMMARY, summary)
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    private fun getDolbySummary(): String {
        val context = context ?: return ""
        val repository = DolbyRepository(context)
        val statusResolver = AutomationStatusResolver(context)
        try {
            if (!repository.getDolbyEnabled()) {
                return context.getString(R.string.dolby_off)
            }

            val profile = repository.getCurrentProfile()
            val profiles = context.resources.getStringArray(R.array.dolby_profile_entries)
            val profileValues = context.resources.getStringArray(R.array.dolby_profile_values)

            val profileName = try {
                val index = profileValues.indexOf(profile.toString())
                if (index != -1) profiles[index] else context.getString(R.string.dolby_unknown)
            } catch (_: Exception) {
                return context.getString(R.string.dolby_on)
            }

            val base = context.getString(R.string.dolby_on_with_profile, profileName)
            val status = statusResolver.resolve()
            val automationSuffix = formatAutomationSuffix(status.activeSource, status.activeDetail)
            return if (automationSuffix != null) "$base · $automationSuffix" else base
        } finally {
            statusResolver.close()
            repository.close()
        }
    }

    private fun formatAutomationSuffix(source: ProfileChangeSource?, detail: String): String? {
        if (source == null) return null
        val trimmed = detail.trim()
        return if (trimmed.isNotEmpty()) {
            "${source.key}: $trimmed"
        } else {
            source.key
        }
    }

    companion object {
        val CONTENT_URI: Uri = Uri.parse("content://org.lunaris.dolby.summary/dolby")
    }
}
