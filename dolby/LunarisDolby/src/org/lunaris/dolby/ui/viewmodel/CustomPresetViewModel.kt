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
import org.lunaris.dolby.domain.models.CustomPresetUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CustomPresetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DolbyRepository(application)

    private val _uiState = MutableStateFlow<CustomPresetUiState>(CustomPresetUiState.Loading)
    val uiState: StateFlow<CustomPresetUiState> = _uiState.asStateFlow()

    fun loadPresets() {
        viewModelScope.launch {
            try {
                val presets = repository.getCustomDolbyPresets()
                _uiState.value = CustomPresetUiState.Success(presets)
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error loading custom presets: ${e.message}")
                _uiState.value = CustomPresetUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun savePreset(name: String): String? {
        return try {
            repository.saveCustomDolbyPreset(name)
            loadPresets()
            null
        } catch (e: IllegalArgumentException) {
            e.message
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error saving custom preset: ${e.message}")
            e.message ?: "Failed to save preset"
        }
    }

    fun applyPreset(name: String, onApplied: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.applyCustomDolbyPreset(name)
                onApplied()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error applying custom preset: ${e.message}")
                _uiState.value = CustomPresetUiState.Error(e.message ?: "Failed to apply preset")
            }
        }
    }

    fun deletePreset(name: String) {
        viewModelScope.launch {
            try {
                repository.deleteCustomDolbyPreset(name)
                loadPresets()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error deleting custom preset: ${e.message}")
                _uiState.value = CustomPresetUiState.Error(e.message ?: "Failed to delete preset")
            }
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    companion object {
        private const val TAG = "CustomPresetViewModel"
    }
}
