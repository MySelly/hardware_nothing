/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import org.lunaris.dolby.domain.models.DolbyDiagnostics

class DolbyStatusHelper(context: Context) {

    private val appContext = context.applicationContext
    private val repository = DolbyRepository(appContext)
    private val historyManager = ProfileChangeHistoryManager(appContext)
    private val statusResolver = AutomationStatusResolver(appContext)

    fun getDiagnostics(): DolbyDiagnostics {
        var hasControl = false
        var effectCreateError: String? = null
        try {
            hasControl = repository.hasEffectControl()
        } catch (e: Exception) {
            effectCreateError = "${e.javaClass.simpleName}: ${e.message}"
            DolbyDiagRecorder.record(appContext, "effect", "hasEffectControl failed", e)
        }
        if (!hasControl) {
            effectCreateError = effectCreateError
                ?: DolbyDiagRecorder.lastEffectError(appContext)
        }
        return DolbyDiagnostics(
            dolbyEnabled = repository.getDolbyEnabled(),
            currentProfile = repository.getCurrentProfile(),
            effectHasControl = hasControl,
            effectCreateError = effectCreateError,
            activeDevice = repository.activeAudioDevice.value,
            automationStatus = statusResolver.resolve(),
            recentHistory = historyManager.getHistory(),
            lastCrash = DolbyDiagRecorder.lastCrash(appContext),
            recentEvents = DolbyDiagRecorder.recentEvents(appContext)
        )
    }

    fun buildShareableReport(profileName: String): String {
        val d = getDiagnostics()
        return DolbyDiagRecorder.buildShareableReport(
            context = appContext,
            dolbyEnabled = d.dolbyEnabled,
            currentProfile = d.currentProfile,
            profileName = profileName,
            effectHasControl = d.effectHasControl,
            effectCreateError = d.effectCreateError,
            activeDevice = d.activeDevice.name,
            activeSource = d.automationStatus.activeSource?.key ?: "-",
            activeDetail = d.automationStatus.activeDetail
        )
    }

    fun close() {
        repository.close()
        statusResolver.close()
    }
}
