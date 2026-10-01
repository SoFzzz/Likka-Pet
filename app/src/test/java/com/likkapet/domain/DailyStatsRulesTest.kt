package com.likkapet.domain

import com.likkapet.domain.model.DailyStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Daily reset (RF-D01) and streak (RF-D04). */
class DailyStatsRulesTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val tuesday = monday.plusDays(1)

    private fun roll(
        stats: DailyStats,
        today: LocalDate = tuesday,
        isLikkaEnabled: Boolean = true,
    ) = DailyStatsRules.rollOver(stats, today, isLikkaEnabled)

    @Test
    fun `a new date zeroes the daily counters`() {
        val stats = DailyStats(date = monday, usageMinutes = 40, interventions = 3, level3Count = 1, pauses = 2)

        val next = roll(stats)

        assertEquals(tuesday, next.date)
        assertEquals(listOf(0, 0, 0, 0), listOf(next.usageMinutes, next.interventions, next.level3Count, next.pauses))
    }

    @Test
    fun `the same date changes nothing`() {
        val stats = DailyStats(date = tuesday, usageMinutes = 12, pauses = 1, streakDays = 4)

        assertSame(stats, roll(stats))
    }

    @Test
    fun `a day with two pauses and no level 3 adds one to the streak`() {
        val stats = DailyStats(date = monday, pauses = 2, streakDays = 4, lastStreakDate = monday.minusDays(1))

        val next = roll(stats)

        assertEquals(5, next.streakDays)
        assertEquals(monday, next.lastStreakDate)
    }

    @Test
    fun `a day with a level 3 resets the streak`() {
        val stats = DailyStats(date = monday, level3Count = 1, streakDays = 4, lastStreakDate = monday.minusDays(1))

        assertEquals(0, roll(stats).streakDays)
    }

    @Test
    fun `switching Likka off during the day resets the streak`() {
        val stats = DailyStats(date = monday, likkaDisabledToday = true, streakDays = 4, lastStreakDate = monday.minusDays(1))

        assertEquals(0, roll(stats).streakDays)
    }

    @Test
    fun `two days without opening the app reset the streak on the third`() {
        val stats = DailyStats(date = monday, streakDays = 4, lastStreakDate = monday.minusDays(1))

        assertEquals(0, roll(stats, today = monday.plusDays(3)).streakDays)
    }

    @Test
    fun `a single unobserved day already breaks the streak`() {
        val stats = DailyStats(date = monday, streakDays = 4, lastStreakDate = monday.minusDays(1))

        assertEquals(0, roll(stats, today = monday.plusDays(2)).streakDays)
    }

    @Test
    fun `a qualifying day after a broken streak starts again at one`() {
        val stats = DailyStats(date = monday, streakDays = 0, lastStreakDate = monday.minusDays(5))

        assertEquals(1, roll(stats).streakDays)
    }

    @Test
    fun `the first day ever starts fresh with no streak`() {
        val next = roll(DailyStats(), today = monday, isLikkaEnabled = false)

        assertEquals(monday, next.date)
        assertEquals(0, next.streakDays)
        assertNull(next.lastStreakDate)
        assertTrue(next.likkaDisabledToday)
    }

    @Test
    fun `the new day starts as switched off only when Likka is off`() {
        assertFalse(roll(DailyStats(date = monday), isLikkaEnabled = true).likkaDisabledToday)
        assertTrue(roll(DailyStats(date = monday), isLikkaEnabled = false).likkaDisabledToday)
    }

    @Test
    fun `a clock moved back starts a new day without touching the streak`() {
        val stats = DailyStats(date = tuesday, usageMinutes = 9, streakDays = 3, lastStreakDate = monday)

        val next = roll(stats, today = monday)

        assertEquals(monday, next.date)
        assertEquals(0, next.usageMinutes)
        assertEquals(3, next.streakDays)
    }
}
