/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class UndoOffer(
    val previousProfileId: Int,
    val detail: String
)

object UndoOfferNotifier {
    private val _offers = MutableSharedFlow<UndoOffer>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val offers: SharedFlow<UndoOffer> = _offers.asSharedFlow()

    fun offer(previousProfileId: Int, detail: String) {
        _offers.tryEmit(UndoOffer(previousProfileId, detail))
    }
}
