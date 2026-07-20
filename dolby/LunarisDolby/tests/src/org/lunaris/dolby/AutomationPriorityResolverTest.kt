/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lunaris.dolby.data.AutomationPriorityResolver
import org.lunaris.dolby.domain.models.ProfileChangeSource

/**
 * Pure-logic tests for [AutomationPriorityResolver.rank] — no Context required.
 */
class AutomationPriorityResolverTest {

    @Test
    fun deviceMode_ranksDeviceAboveApp() {
        val device = AutomationPriorityResolver.rank(
            ProfileChangeSource.DEVICE,
            DolbyConstants.PROFILE_PRIORITY_DEVICE
        )
        val app = AutomationPriorityResolver.rank(
            ProfileChangeSource.APP,
            DolbyConstants.PROFILE_PRIORITY_DEVICE
        )
        assertTrue(device != null && app != null)
        assertTrue(device!! > app!!)
    }

    @Test
    fun appMode_ranksAppAboveDevice() {
        val device = AutomationPriorityResolver.rank(
            ProfileChangeSource.DEVICE,
            DolbyConstants.PROFILE_PRIORITY_APP
        )
        val app = AutomationPriorityResolver.rank(
            ProfileChangeSource.APP,
            DolbyConstants.PROFILE_PRIORITY_APP
        )
        assertTrue(device != null && app != null)
        assertTrue(app!! > device!!)
    }

    @Test
    fun bluetooth_sharesDeviceRank() {
        val device = AutomationPriorityResolver.rank(
            ProfileChangeSource.DEVICE,
            DolbyConstants.PROFILE_PRIORITY_DEVICE
        )
        val bluetooth = AutomationPriorityResolver.rank(
            ProfileChangeSource.BLUETOOTH,
            DolbyConstants.PROFILE_PRIORITY_DEVICE
        )
        assertEquals(device, bluetooth)
    }

    @Test
    fun manual_isUnranked() {
        assertNull(
            AutomationPriorityResolver.rank(
                ProfileChangeSource.MANUAL,
                DolbyConstants.PROFILE_PRIORITY_DEVICE
            )
        )
    }

    @Test
    fun schedule_ranksAboveMedia_inBothModes() {
        for (mode in listOf(
            DolbyConstants.PROFILE_PRIORITY_DEVICE,
            DolbyConstants.PROFILE_PRIORITY_APP
        )) {
            val schedule = AutomationPriorityResolver.rank(ProfileChangeSource.SCHEDULE, mode)!!
            val media = AutomationPriorityResolver.rank(ProfileChangeSource.MEDIA, mode)!!
            val focus = AutomationPriorityResolver.rank(ProfileChangeSource.FOCUS, mode)!!
            assertTrue(schedule > media)
            assertTrue(media > focus)
        }
    }
}
