/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.data.DolbyAutomationCoordinator
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.data.MediaContentRulesManager
import org.lunaris.dolby.data.SleepTimerManager
import org.lunaris.dolby.domain.models.SleepTimerAction
import org.lunaris.dolby.domain.models.SleepTimerConfig
import org.lunaris.dolby.ui.theme.DolbyAppearanceState
import org.lunaris.dolby.widget.DolbyWidgetProvider

data class AutomationSettingsUiState(
    val batterySaver: Boolean = false,
    val autoCall: Boolean = true,
    val mediaDetect: Boolean = false,
    val gameLatency: Boolean = false,
    val focusMode: Boolean = false,
    val simpleUi: Boolean = false,
    val amoled: Boolean = false,
    val safeLimit: Float = 0f,
    val sleepMinutes: Int = 30,
    val widgetSleepMinutes: Int = DolbyConstants.DEFAULT_WIDGET_SLEEP_MINUTES,
    val activeTimer: SleepTimerConfig? = null,
    val musicProfile: Int = MediaContentRulesManager.defaultProfileFor("music"),
    val videoProfile: Int = MediaContentRulesManager.defaultProfileFor("video"),
    val gameProfile: Int = MediaContentRulesManager.defaultProfileFor("game"),
    val speechProfile: Int = MediaContentRulesManager.defaultProfileFor("speech"),
    val packageOverrides: Map<String, String> = emptyMap()
)

class AutomationSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
    private val mediaRules = MediaContentRulesManager(application)
    private val timerManager = SleepTimerManager(application)

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<AutomationSettingsUiState> = _uiState.asStateFlow()

    private fun loadState(): AutomationSettingsUiState {
        val widgetSleep = prefs.getInt(
            DolbyConstants.PREF_WIDGET_SLEEP_MINUTES,
            DolbyConstants.DEFAULT_WIDGET_SLEEP_MINUTES
        ).let { mins ->
            if (mins in DolbyConstants.WIDGET_SLEEP_MINUTE_OPTIONS) mins
            else DolbyConstants.DEFAULT_WIDGET_SLEEP_MINUTES
        }
        return AutomationSettingsUiState(
            batterySaver = prefs.getBoolean(DolbyConstants.PREF_BATTERY_SAVER_MODE, false),
            autoCall = prefs.getBoolean(DolbyConstants.PREF_AUTO_DISABLE_ON_CALL, true),
            mediaDetect = prefs.getBoolean(DolbyConstants.PREF_MEDIA_CONTENT_DETECTION, false),
            gameLatency = prefs.getBoolean(DolbyConstants.PREF_GAME_LATENCY_MODE, false),
            focusMode = prefs.getBoolean(DolbyConstants.PREF_FOCUS_MODE_INTEGRATION, false),
            simpleUi = prefs.getBoolean(DolbyConstants.PREF_SIMPLE_UI_MODE, false),
            amoled = prefs.getBoolean(DolbyConstants.PREF_AMOLED_THEME, false),
            safeLimit = prefs.getInt(DolbyConstants.PREF_SAFE_LISTENING_LIMIT, 0).toFloat(),
            sleepMinutes = 30,
            widgetSleepMinutes = widgetSleep,
            activeTimer = timerManager.getActiveTimer(),
            musicProfile = mediaRules.getProfileForContentType("music"),
            videoProfile = mediaRules.getProfileForContentType("video"),
            gameProfile = mediaRules.getProfileForContentType("game"),
            speechProfile = mediaRules.getProfileForContentType("speech"),
            packageOverrides = mediaRules.getPackageOverridesMap()
        )
    }

    fun setBatterySaver(enabled: Boolean) {
        prefs.edit().putBoolean(DolbyConstants.PREF_BATTERY_SAVER_MODE, enabled).apply()
        _uiState.update { it.copy(batterySaver = enabled) }
    }

    fun setAutoCall(enabled: Boolean) {
        prefs.edit().putBoolean(DolbyConstants.PREF_AUTO_DISABLE_ON_CALL, enabled).apply()
        _uiState.update { it.copy(autoCall = enabled) }
    }

    fun setMediaDetect(enabled: Boolean) {
        prefs.edit().putBoolean(DolbyConstants.PREF_MEDIA_CONTENT_DETECTION, enabled).apply()
        _uiState.update { it.copy(mediaDetect = enabled) }
    }

    fun setGameLatency(enabled: Boolean) {
        prefs.edit().putBoolean(DolbyConstants.PREF_GAME_LATENCY_MODE, enabled).apply()
        _uiState.update { it.copy(gameLatency = enabled) }
        DolbyAutomationCoordinator.applyGameLatencyMode(getApplication())
    }

    fun setFocusMode(enabled: Boolean) {
        prefs.edit().putBoolean(DolbyConstants.PREF_FOCUS_MODE_INTEGRATION, enabled).apply()
        _uiState.update { it.copy(focusMode = enabled) }
    }

    fun setSimpleUi(enabled: Boolean) {
        prefs.edit().putBoolean(DolbyConstants.PREF_SIMPLE_UI_MODE, enabled).apply()
        _uiState.update { it.copy(simpleUi = enabled) }
        DolbyAppearanceState.notifyChanged()
    }

    fun setAmoled(enabled: Boolean) {
        prefs.edit().putBoolean(DolbyConstants.PREF_AMOLED_THEME, enabled).apply()
        _uiState.update { it.copy(amoled = enabled) }
        DolbyAppearanceState.notifyChanged()
    }

    fun setSafeLimit(limit: Float) {
        prefs.edit().putInt(DolbyConstants.PREF_SAFE_LISTENING_LIMIT, limit.toInt()).apply()
        _uiState.update { it.copy(safeLimit = limit) }
        DolbyAutomationCoordinator.enforceSafeListeningLimit(getApplication())
    }

    fun setSleepMinutes(minutes: Int) {
        _uiState.update { it.copy(sleepMinutes = minutes) }
    }

    fun setWidgetSleepMinutes(minutes: Int) {
        val sanitized = if (minutes in DolbyConstants.WIDGET_SLEEP_MINUTE_OPTIONS) {
            minutes
        } else {
            DolbyConstants.DEFAULT_WIDGET_SLEEP_MINUTES
        }
        prefs.edit().putInt(DolbyConstants.PREF_WIDGET_SLEEP_MINUTES, sanitized).apply()
        _uiState.update { it.copy(widgetSleepMinutes = sanitized) }
        DolbyWidgetProvider.refreshAll(getApplication())
    }

    fun startSleepTimer() {
        val minutes = _uiState.value.sleepMinutes
        val repo = DolbyRepository(getApplication())
        val current = try {
            repo.getCurrentProfile()
        } finally {
            repo.close()
        }
        timerManager.startTimer(minutes, SleepTimerAction.RESTORE_PROFILE, 0, current)
        _uiState.update { it.copy(activeTimer = timerManager.getActiveTimer()) }
    }

    fun cancelSleepTimer() {
        timerManager.clearTimer()
        _uiState.update { it.copy(activeTimer = null) }
    }

    fun setMediaProfile(contentType: String, profileId: Int) {
        mediaRules.setProfileForContentType(contentType, profileId)
        _uiState.update {
            when (contentType) {
                "music" -> it.copy(musicProfile = profileId)
                "video" -> it.copy(videoProfile = profileId)
                "game" -> it.copy(gameProfile = profileId)
                "speech" -> it.copy(speechProfile = profileId)
                else -> it
            }
        }
    }

    fun addPackageOverride(packageName: String, contentType: String) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        mediaRules.setPackageContentType(pkg, contentType)
        _uiState.update { it.copy(packageOverrides = mediaRules.getPackageOverridesMap()) }
    }

    fun removePackageOverride(packageName: String) {
        mediaRules.setPackageContentType(packageName, null)
        _uiState.update { it.copy(packageOverrides = mediaRules.getPackageOverridesMap()) }
    }
}
