/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bumps when appearance prefs (AMOLED, simple UI, dynamic color) change so Compose
 * themes and toolbars recompose immediately without restarting the activity.
 */
object DolbyAppearanceState {
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun notifyChanged() {
        _revision.value = _revision.value + 1
    }
}
