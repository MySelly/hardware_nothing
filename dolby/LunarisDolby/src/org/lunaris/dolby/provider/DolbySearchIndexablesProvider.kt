/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.provider

import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.provider.SearchIndexablesContract
import android.provider.SearchIndexablesProvider
import org.lunaris.dolby.R
import org.lunaris.dolby.ui.DolbyActivity

/**
 * Settings Intelligence raw indexables for Dolby Atmos (title + keywords).
 */
class DolbySearchIndexablesProvider : SearchIndexablesProvider() {

    override fun onCreate(): Boolean = true

    override fun queryXmlResources(projection: Array<out String>?): Cursor {
        return MatrixCursor(SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS)
    }

    override fun queryRawData(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(SearchIndexablesContract.INDEXABLES_RAW_COLUMNS)
        val ctx = context ?: return cursor
        val title = ctx.getString(R.string.dolby_title)
        val keywords = ctx.resources
            .getStringArray(R.array.dolby_settings_search_keywords)
            .joinToString(",")
        // Column order matches SearchIndexablesContract.INDEXABLES_RAW_COLUMNS.
        cursor.addRow(
            arrayOf<Any?>(
                /* RANK */ 1,
                /* TITLE */ title,
                /* SUMMARY_ON */ null,
                /* SUMMARY_OFF */ null,
                /* ENTRIES */ null,
                /* KEYWORDS */ keywords,
                /* SCREEN_TITLE */ title,
                /* CLASS_NAME */ DolbyActivity::class.java.name,
                /* ICON_RESID */ R.mipmap.ic_launcher,
                /* INTENT_ACTION */ Intent.ACTION_MAIN,
                /* INTENT_TARGET_PACKAGE */ ctx.packageName,
                /* INTENT_TARGET_CLASS */ DolbyActivity::class.java.name,
                /* KEY */ KEY_DOLBY,
                /* USER_ID */ -1
            )
        )
        return cursor
    }

    override fun queryNonIndexableKeys(projection: Array<out String>?): Cursor {
        return MatrixCursor(SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS)
    }

    companion object {
        const val KEY_DOLBY = "dolby_atmos"
    }
}
