/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.data.DeviceStateManager
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.domain.models.DeviceMemoryUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceMemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DolbyRepository(application)
    private val deviceStateManager = DeviceStateManager(application)
    private val defaultPrefs = application.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<DeviceMemoryUiState>(DeviceMemoryUiState.Loading)
    val uiState: StateFlow<DeviceMemoryUiState> = _uiState.asStateFlow()

    fun loadSnapshots() {
        viewModelScope.launch {
            try {
                repository.updateSpeakerState()
                val currentDevice = repository.getCurrentOutputDevice()
                val currentKey = currentDevice?.let { deviceStateManager.deviceKey(it) }
                val snapshots = deviceStateManager.getSnapshotSummaries(currentKey)
                val isMemoryEnabled = defaultPrefs.getBoolean(DolbyConstants.PREF_DEVICE_STATE_MEMORY, false)
                _uiState.value = DeviceMemoryUiState.Success(snapshots, isMemoryEnabled)
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error loading device snapshots: ${e.message}")
                _uiState.value = DeviceMemoryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun deleteSnapshot(deviceKey: String) {
        viewModelScope.launch {
            deviceStateManager.clearSnapshot(deviceKey)
            loadSnapshots()
        }
    }

    fun clearAllSnapshots() {
        viewModelScope.launch {
            deviceStateManager.clearAllSnapshots()
            loadSnapshots()
        }
    }

    fun restoreSnapshot(deviceKey: String) {
        viewModelScope.launch {
            try {
                deviceStateManager.restoreSnapshot(deviceKey, repository)
                loadSnapshots()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error restoring snapshot: ${e.message}")
                _uiState.value = DeviceMemoryUiState.Error(e.message ?: "Failed to restore snapshot")
            }
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    companion object {
        private const val TAG = "DeviceMemoryViewModel"
    }
}
