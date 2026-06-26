/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.data.DolbyAutomationCoordinator
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.data.ProfileChangeHistoryManager
import org.lunaris.dolby.domain.models.ProfileChangeSource
import org.lunaris.dolby.service.DolbyEffectService

class DolbyAutomationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DolbyConstants.ACTION_SET_PROFILE -> {
                val profile = intent.getIntExtra(DolbyConstants.EXTRA_PROFILE, -1)
                if (profile >= 0) {
                    DolbyAutomationCoordinator.applyProfileChange(
                        context,
                        profile,
                        ProfileChangeSource.AUTOMATION,
                        "External intent"
                    )
                    DolbyEffectService.start(context)
                }
            }
            DolbyConstants.ACTION_TOGGLE -> {
                val repository = DolbyRepository(context)
                try {
                    val enabled = !repository.getDolbyEnabled()
                    repository.setDolbyEnabled(enabled)
                    ProfileChangeHistoryManager(context).recordChange(
                        repository.getCurrentProfile(),
                        ProfileChangeSource.AUTOMATION,
                        if (enabled) "Enabled via intent" else "Disabled via intent"
                    )
                    if (enabled) DolbyEffectService.start(context) else DolbyEffectService.stop(context)
                } finally {
                    repository.close()
                }
            }
            DolbyConstants.ACTION_SET_ENABLED -> {
                val enabled = intent.getBooleanExtra(DolbyConstants.EXTRA_ENABLED, true)
                val repository = DolbyRepository(context)
                try {
                    repository.setDolbyEnabled(enabled)
                    if (enabled) DolbyEffectService.start(context) else DolbyEffectService.stop(context)
                } finally {
                    repository.close()
                }
            }
            DolbyConstants.ACTION_APPLY_PRESET -> {
                val preset = intent.getStringExtra(DolbyConstants.EXTRA_PRESET) ?: return
                val repository = DolbyRepository(context)
                try {
                    repository.applyCustomDolbyPreset(preset)
                    ProfileChangeHistoryManager(context).recordChange(
                        repository.getCurrentProfile(),
                        ProfileChangeSource.AUTOMATION,
                        "Preset: $preset"
                    )
                } catch (e: Exception) {
                    DolbyConstants.dlog(TAG, "Failed to apply preset: ${e.message}")
                } finally {
                    repository.close()
                }
            }
            Intent.ACTION_POWER_SAVE_MODE_CHANGED -> {
                DolbyAutomationCoordinator.applyBatterySaverIfNeeded(context)
            }
            "android.app.action.INTERRUPTION_FILTER_CHANGED" -> {
                val zen = intent.getIntExtra("android.app.extra.INTERRUPTION_FILTER_TYPE", -1)
                DolbyAutomationCoordinator.applyFocusModeProfile(context, zen > 0)
            }
        }
    }

    companion object {
        private const val TAG = "DolbyAutomationRx"
    }
}
