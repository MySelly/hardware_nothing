/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lunaris.dolby.domain.models.ScheduledProfileRule
import java.util.Calendar

/**
 * Pure logic tests for [ScheduledProfileRule] day/minute matching (no Context).
 */
class ScheduledProfileRuleTest {

    private fun rule(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        days: Set<Int> = emptySet()
    ) = ScheduledProfileRule(
        id = "test",
        name = "Test",
        profileId = 0,
        startHour = startHour,
        startMinute = startMinute,
        endHour = endHour,
        endMinute = endMinute,
        daysOfWeek = days
    )

    @Test
    fun containsMinute_sameDayWindow() {
        val r = rule(9, 0, 17, 0)
        assertTrue(r.containsMinute(9 * 60))
        assertTrue(r.containsMinute(12 * 60 + 30))
        assertFalse(r.containsMinute(17 * 60))
        assertFalse(r.containsMinute(8 * 60 + 59))
    }

    @Test
    fun containsMinute_overnightWindow() {
        val r = rule(22, 0, 6, 0)
        assertTrue(r.containsMinute(22 * 60))
        assertTrue(r.containsMinute(23 * 60 + 30))
        assertTrue(r.containsMinute(0))
        assertTrue(r.containsMinute(5 * 60 + 59))
        assertFalse(r.containsMinute(6 * 60))
        assertFalse(r.containsMinute(12 * 60))
    }

    @Test
    fun matchesDay_emptyMeansEveryDay() {
        val r = rule(0, 0, 1, 0, days = emptySet())
        assertTrue(r.matchesDay(Calendar.SUNDAY))
        assertTrue(r.matchesDay(Calendar.WEDNESDAY))
        assertTrue(r.matchesDay(Calendar.SATURDAY))
    }

    @Test
    fun matchesDay_specificDaysOnly() {
        val r = rule(
            0, 0, 1, 0,
            days = setOf(Calendar.MONDAY, Calendar.FRIDAY)
        )
        assertTrue(r.matchesDay(Calendar.MONDAY))
        assertTrue(r.matchesDay(Calendar.FRIDAY))
        assertFalse(r.matchesDay(Calendar.TUESDAY))
        assertFalse(r.matchesDay(Calendar.SUNDAY))
    }
}
