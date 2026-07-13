/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import org.lunaris.dolby.domain.models.DolbyDiagnostics

class DolbyStatusHelper(context: Context) {

    private val repository = DolbyRepository(context)
    private val historyManager = ProfileChangeHistoryManager(context)
    private val statusResolver = AutomationStatusResolver(context)

    fun getDiagnostics(): DolbyDiagnostics {
        var hasControl = false
        try {
            hasControl = repository.hasEffectControl()
        } catch (_: Exception) {
        }
        return DolbyDiagnostics(
            dolbyEnabled = repository.getDolbyEnabled(),
            currentProfile = repository.getCurrentProfile(),
            effectHasControl = hasControl,
            activeDevice = repository.activeAudioDevice.value,
            automationStatus = statusResolver.resolve(),
            recentHistory = historyManager.getHistory()
        )
    }

    fun close() {
        repository.close()
        statusResolver.close()
    }
}
