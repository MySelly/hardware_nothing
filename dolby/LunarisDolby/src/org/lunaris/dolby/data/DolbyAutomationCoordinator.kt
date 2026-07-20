/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.PowerManager
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.domain.models.ProfileChangeSource
import org.lunaris.dolby.domain.models.SleepTimerAction

object DolbyAutomationCoordinator {

    fun applyProfileChange(
        context: Context,
        profileId: Int,
        source: ProfileChangeSource,
        detail: String,
        saveUndo: Boolean = true
    ) {
        val repository = DolbyRepository(context)
        val history = ProfileChangeHistoryManager(context)
        try {
            if (saveUndo) {
                val previous = repository.getCurrentProfile()
                history.saveUndoProfile(previous)
                UndoOfferNotifier.offer(previous, detail)
            }
            repository.setCurrentProfile(profileId)
            history.recordChange(profileId, source, detail)
        } finally {
            repository.close()
        }
    }

    fun applyBluetoothRules(context: Context, device: AudioDeviceInfo?) {
        if (device == null) return
        val btManager = BluetoothProfileManager(context)
        val rule = btManager.findRuleForDevice(device) ?: return
        applyProfileChange(
            context,
            rule.profileId,
            ProfileChangeSource.BLUETOOTH,
            rule.displayName,
            saveUndo = false
        )
    }

    fun applyMediaContentProfile(context: Context, contentType: String) {
        val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(DolbyConstants.PREF_MEDIA_CONTENT_DETECTION, false)) return

        val rules = MediaContentRulesManager(context)
        val profileId = rules.getProfileForContentType(contentType)
        if (profileId < 0) return

        prefs.edit().putString("last_media_content_type", contentType).apply()
        applyProfileChange(
            context,
            profileId,
            ProfileChangeSource.MEDIA,
            contentType,
            saveUndo = false
        )
    }

    fun handleCallState(context: Context, inCall: Boolean) {
        val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(DolbyConstants.PREF_AUTO_DISABLE_ON_CALL, true)) return

        val repository = DolbyRepository(context)
        val history = ProfileChangeHistoryManager(context)
        try {
            if (inCall) {
                prefs.edit()
                    .putBoolean("call_dolby_was_enabled", repository.getDolbyEnabled())
                    .putInt("call_previous_profile", repository.getCurrentProfile())
                    .apply()
                if (repository.getDolbyEnabled()) {
                    repository.setDolbyBypass(true)
                    history.recordChange(
                        repository.getCurrentProfile(),
                        ProfileChangeSource.CALL,
                        "Bypass during call"
                    )
                }
            } else {
                val wasEnabled = prefs.getBoolean("call_dolby_was_enabled", true)
                repository.setDolbyBypass(false)
                if (wasEnabled && !repository.getDolbyEnabled()) {
                    repository.setDolbyEnabled(true)
                }
                history.recordChange(
                    repository.getCurrentProfile(),
                    ProfileChangeSource.CALL,
                    "Call ended"
                )
            }
        } finally {
            repository.close()
        }
    }

    fun applyBatterySaverIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(DolbyConstants.PREF_BATTERY_SAVER_MODE, false)) return

        val powerManager = context.getSystemService(PowerManager::class.java) ?: return
        if (!powerManager.isPowerSaveMode) return

        val repository = DolbyRepository(context)
        try {
            val profile = repository.getCurrentProfile()
            repository.setHeadphoneVirtualizerEnabled(profile, false)
            repository.setSpeakerVirtualizerEnabled(profile, false)
            repository.setSurroundBoost(profile, false, 0)
        } finally {
            repository.close()
        }
    }

    fun applyGameLatencyMode(context: Context) {
        val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(DolbyConstants.PREF_GAME_LATENCY_MODE, false)) return

        val repository = DolbyRepository(context)
        try {
            val profile = repository.getCurrentProfile()
            if (profile != 3) return
            repository.setHeadphoneVirtualizerEnabled(profile, false)
            repository.setSpeakerVirtualizerEnabled(profile, false)
            repository.setVolumeLevelerEnabled(profile, false)
            repository.setSurroundBoost(profile, false, 0)
        } finally {
            repository.close()
        }
    }

    fun applyFocusModeProfile(context: Context, focusActive: Boolean) {
        val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(DolbyConstants.PREF_FOCUS_MODE_INTEGRATION, false)) return

        val alreadyApplied = prefs.getBoolean(DolbyConstants.PREF_FOCUS_APPLIED, false)
        if (focusActive) {
            if (alreadyApplied) return
            val repository = DolbyRepository(context)
            try {
                val current = repository.getCurrentProfile()
                val focusProfile = prefs.getInt(DolbyConstants.PREF_FOCUS_PROFILE_ID, 4)
                if (current == focusProfile) {
                    prefs.edit()
                        .putInt(DolbyConstants.PREF_FOCUS_PREVIOUS_PROFILE, current)
                        .putBoolean(DolbyConstants.PREF_FOCUS_APPLIED, true)
                        .apply()
                    return
                }
                prefs.edit()
                    .putInt(DolbyConstants.PREF_FOCUS_PREVIOUS_PROFILE, current)
                    .putBoolean(DolbyConstants.PREF_FOCUS_APPLIED, true)
                    .apply()
                applyProfileChange(
                    context,
                    focusProfile,
                    ProfileChangeSource.FOCUS,
                    "Focus mode",
                    saveUndo = false
                )
            } finally {
                repository.close()
            }
        } else if (alreadyApplied) {
            val previous = prefs.getInt(DolbyConstants.PREF_FOCUS_PREVIOUS_PROFILE, -1)
            prefs.edit()
                .putBoolean(DolbyConstants.PREF_FOCUS_APPLIED, false)
                .remove(DolbyConstants.PREF_FOCUS_PREVIOUS_PROFILE)
                .apply()
            if (previous >= 0) {
                applyProfileChange(
                    context,
                    previous,
                    ProfileChangeSource.FOCUS,
                    "Focus mode ended",
                    saveUndo = false
                )
            }
        }
    }

    fun isFocusInterruptionFilter(filter: Int): Boolean {
        return filter == android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
            filter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE ||
            filter == android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
    }

    fun enforceSafeListeningLimit(context: Context) {
        val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
        val limit = prefs.getInt(DolbyConstants.PREF_SAFE_LISTENING_LIMIT, 0)
        if (limit <= 0) return

        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cap = (max * limit / 100f).toInt().coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current > cap) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0)
        }
    }

    fun handleSleepTimerFire(context: Context) {
        val timerManager = SleepTimerManager(context)
        val config = timerManager.getActiveTimer() ?: return
        timerManager.clearTimer()

        when (config.action) {
            SleepTimerAction.DISABLE -> {
                val repository = DolbyRepository(context)
                try {
                    repository.setDolbyEnabled(false)
                } finally {
                    repository.close()
                }
            }
            SleepTimerAction.SWITCH_PROFILE -> {
                applyProfileChange(
                    context,
                    config.targetProfileId,
                    ProfileChangeSource.SLEEP_TIMER,
                    "Sleep timer"
                )
            }
            SleepTimerAction.RESTORE_PROFILE -> {
                applyProfileChange(
                    context,
                    config.previousProfileId,
                    ProfileChangeSource.SLEEP_TIMER,
                    "Sleep timer restore"
                )
            }
        }
    }
}
