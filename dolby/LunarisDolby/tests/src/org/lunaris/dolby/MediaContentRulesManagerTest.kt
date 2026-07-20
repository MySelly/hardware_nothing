/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lunaris.dolby.data.MediaContentRulesManager

/**
 * Pure-logic tests for [MediaContentRulesManager] defaults — no Context required.
 */
class MediaContentRulesManagerTest {

    @Test
    fun defaultProfiles_matchExpectedIds() {
        assertEquals(2, MediaContentRulesManager.defaultProfileFor("music"))
        assertEquals(1, MediaContentRulesManager.defaultProfileFor("video"))
        assertEquals(1, MediaContentRulesManager.defaultProfileFor("movie"))
        assertEquals(3, MediaContentRulesManager.defaultProfileFor("game"))
        assertEquals(4, MediaContentRulesManager.defaultProfileFor("speech"))
        assertEquals(4, MediaContentRulesManager.defaultProfileFor("podcast"))
        assertEquals(-1, MediaContentRulesManager.defaultProfileFor("unknown"))
    }

    @Test
    fun defaultPackageTypes_coverCommonApps() {
        assertEquals("music", MediaContentRulesManager.DEFAULT_PACKAGE_TYPES["com.spotify.music"])
        assertEquals("video", MediaContentRulesManager.DEFAULT_PACKAGE_TYPES["com.netflix.mediaclient"])
        assertEquals("game", MediaContentRulesManager.DEFAULT_PACKAGE_TYPES["com.mojang.minecraftpe"])
        assertEquals("speech", MediaContentRulesManager.DEFAULT_PACKAGE_TYPES["com.audible.application"])
    }

    @Test
    fun contentTypes_areComplete() {
        assertEquals(
            listOf("music", "video", "game", "speech"),
            MediaContentRulesManager.CONTENT_TYPES
        )
        assertTrue(MediaContentRulesManager.CONTENT_TYPES.all {
            MediaContentRulesManager.defaultProfileFor(it) >= 0
        })
    }
}
