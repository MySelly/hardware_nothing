/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.data.ScheduledProfileManager
import org.lunaris.dolby.domain.models.ScheduledProfileRule
import org.lunaris.dolby.domain.models.ScheduledProfileUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScheduledProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = ScheduledProfileManager(application)
    private val repository = DolbyRepository(application)

    private val _uiState = MutableStateFlow<ScheduledProfileUiState>(ScheduledProfileUiState.Loading)
    val uiState: StateFlow<ScheduledProfileUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            try {
                _uiState.value = ScheduledProfileUiState.Success(
                    rules = manager.getRules(),
                    enabled = manager.isEnabled()
                )
            } catch (e: Exception) {
                _uiState.value = ScheduledProfileUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        manager.setEnabled(enabled)
        if (enabled) {
            manager.applyActiveRuleIfNeeded(repository)
        }
        load()
    }

    fun addRule(
        name: String,
        profileId: Int,
        startHour: Int,
        endHour: Int,
        daysOfWeek: Set<Int> = emptySet(),
        priority: Int = 0
    ) {
        val rule = ScheduledProfileRule(
            id = ScheduledProfileManager.newRuleId(),
            name = name.trim(),
            profileId = profileId,
            startHour = startHour.coerceIn(0, 23),
            startMinute = 0,
            endHour = endHour.coerceIn(0, 23),
            endMinute = 0,
            daysOfWeek = daysOfWeek,
            priority = priority
        )
        manager.addRule(rule)
        load()
    }

    fun deleteRule(id: String) {
        manager.deleteRule(id)
        load()
    }

    fun toggleRule(id: String, enabled: Boolean) {
        val updated = manager.getRules().map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        manager.saveRules(updated)
        load()
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    companion object {
        private const val TAG = "ScheduledProfileViewModel"
    }
}
