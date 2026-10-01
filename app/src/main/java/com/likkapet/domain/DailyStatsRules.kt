package com.likkapet.domain

import com.likkapet.domain.model.DailyStats
import java.time.LocalDate

/**
 * Daily reset (RF-D01) and streak (RF-D04). A day adds to the streak when Likka was never switched
 * off during it (pauses do not count as off) and no level 3 happened. A day nobody observed (the
 * store was not touched) does not qualify, so a gap of more than one day resets the streak.
 */
object DailyStatsRules {
    /** Moves [stats] to [today]; [isLikkaEnabled] seeds the new day's "switched off" flag. */
    fun rollOver(
        stats: DailyStats,
        today: LocalDate,
        isLikkaEnabled: Boolean,
    ): DailyStats {
        val previousDay = stats.date ?: return freshDay(stats, today, isLikkaEnabled)
        if (previousDay == today) return stats
        // The clock went back (manual change): start a new day but leave the streak alone.
        if (previousDay.isAfter(today)) return freshDay(stats, today, isLikkaEnabled)
        val evaluated = evaluateDay(stats, previousDay)
        val streakDays = if (previousDay.plusDays(1) == today) evaluated.streakDays else 0
        return freshDay(evaluated.copy(streakDays = streakDays), today, isLikkaEnabled)
    }

    private fun evaluateDay(
        stats: DailyStats,
        day: LocalDate,
    ): DailyStats {
        if (!qualifies(stats)) return stats.copy(streakDays = 0)
        val continuesStreak = stats.lastStreakDate == day.minusDays(1)
        val streakDays = if (continuesStreak) stats.streakDays + 1 else 1
        return stats.copy(streakDays = streakDays, lastStreakDate = day)
    }

    private fun qualifies(stats: DailyStats): Boolean = !stats.likkaDisabledToday && stats.level3Count == 0

    private fun freshDay(
        stats: DailyStats,
        today: LocalDate,
        isLikkaEnabled: Boolean,
    ): DailyStats =
        DailyStats(
            date = today,
            likkaDisabledToday = !isLikkaEnabled,
            streakDays = stats.streakDays,
            lastStreakDate = stats.lastStreakDate,
        )
}
