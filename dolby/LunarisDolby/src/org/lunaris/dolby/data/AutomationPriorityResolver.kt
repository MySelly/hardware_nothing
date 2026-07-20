/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.domain.models.ProfileChangeSource

/**
 * Automation priority engine.
 *
 * Reads [DolbyConstants.PREF_PROFILE_PRIORITY] and decides whether an incoming
 * automation source (APP, DEVICE/BT, SCHEDULE, MEDIA, FOCUS) may override the
 * last applied ranked source.
 *
 * Priority modes (higher rank wins; equal rank is allowed so the same tier can refresh):
 * - [DolbyConstants.PROFILE_PRIORITY_DEVICE]: DEVICE/BT > APP > SCHEDULE > MEDIA > FOCUS
 * - [DolbyConstants.PROFILE_PRIORITY_APP]: APP > DEVICE/BT > SCHEDULE > MEDIA > FOCUS
 *
 * Conservative rules:
 * - Only the ranked sources above participate. MANUAL / WIDGET / QS_TILE / CALL /
 *   SLEEP_TIMER / AUTOMATION always apply and do not block later automation
 *   (they are treated as unranked when inspecting the last source).
 * - BLUETOOTH shares DEVICE's rank (device-bound rules).
 * - Incoming source is blocked only when it ranks *strictly lower* than the last
 *   ranked applied source, unless [force] is true.
 * - Callers that restore a previous profile after a temporary override
 *   (e.g. focus mode ended) should pass force=true.
 */
class AutomationPriorityResolver(context: Context) {

    private val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)

    fun getPriorityMode(): String {
        return prefs.getString(
            DolbyConstants.PREF_PROFILE_PRIORITY,
            DolbyConstants.PROFILE_PRIORITY_DEVICE
        ) ?: DolbyConstants.PROFILE_PRIORITY_DEVICE
    }

    /**
     * Rank for [source] under the active priority mode.
     * Higher value = higher priority. Null means the source is unranked
     * and should never be blocked by this engine.
     */
    fun rankOf(source: ProfileChangeSource): Int? {
        val order = rankedOrder(getPriorityMode())
        val normalized = normalize(source)
        val index = order.indexOf(normalized)
        if (index < 0) return null
        // First in list = highest priority
        return order.size - index
    }

    /**
     * @return true if the incoming change should be applied.
     */
    fun shouldAllow(incoming: ProfileChangeSource, force: Boolean = false): Boolean {
        if (force) return true

        val incomingRank = rankOf(incoming) ?: return true
        val lastRank = lastAppliedRank() ?: return true

        val allowed = incomingRank >= lastRank
        if (!allowed) {
            DolbyConstants.dlog(
                TAG,
                "Blocked ${incoming.key} (rank=$incomingRank) by last ranked source " +
                    "(rank=$lastRank, mode=${getPriorityMode()})"
            )
        }
        return allowed
    }

    /**
     * Persist the last applied automation source used for ranking.
     * Prefer calling this only after a successful apply of a ranked source.
     * [ProfileChangeHistoryManager.recordChange] also writes the same prefs keys;
     * this method exists so callers that skip history can still update ranking.
     */
    fun markApplied(source: ProfileChangeSource, detail: String = "") {
        prefs.edit()
            .putString(DolbyConstants.PREF_LAST_AUTOMATION_SOURCE, source.key)
            .putString(DolbyConstants.PREF_LAST_AUTOMATION_DETAIL, detail)
            .apply()
    }

    fun lastAppliedSource(): ProfileChangeSource? {
        val key = prefs.getString(DolbyConstants.PREF_LAST_AUTOMATION_SOURCE, null) ?: return null
        return ProfileChangeSource.entries.find { it.key == key }
    }

    private fun lastAppliedRank(): Int? {
        val last = lastAppliedSource() ?: return null
        return rankOf(last)
    }

    private fun normalize(source: ProfileChangeSource): ProfileChangeSource {
        return when (source) {
            ProfileChangeSource.BLUETOOTH -> ProfileChangeSource.DEVICE
            else -> source
        }
    }

    private fun rankedOrder(mode: String): List<ProfileChangeSource> {
        return when (mode) {
            DolbyConstants.PROFILE_PRIORITY_APP -> ORDER_APP_FIRST
            else -> ORDER_DEVICE_FIRST
        }
    }

    companion object {
        private const val TAG = "AutoPriority"

        /** DEVICE/BT > APP > SCHEDULE > MEDIA > FOCUS */
        private val ORDER_DEVICE_FIRST = listOf(
            ProfileChangeSource.DEVICE,
            ProfileChangeSource.APP,
            ProfileChangeSource.SCHEDULE,
            ProfileChangeSource.MEDIA,
            ProfileChangeSource.FOCUS
        )

        /** APP > DEVICE/BT > SCHEDULE > MEDIA > FOCUS */
        private val ORDER_APP_FIRST = listOf(
            ProfileChangeSource.APP,
            ProfileChangeSource.DEVICE,
            ProfileChangeSource.SCHEDULE,
            ProfileChangeSource.MEDIA,
            ProfileChangeSource.FOCUS
        )
    }
}
