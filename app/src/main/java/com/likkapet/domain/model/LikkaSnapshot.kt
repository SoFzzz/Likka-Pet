package com.likkapet.domain.model

import com.likkapet.domain.EscalationConfig
import java.time.LocalDate

/** User choices persisted by the stats store (RF-D01); none of them reset with `today_date`. */
data class LikkaSettings(
    val onboardingCompleted: Boolean = false,
    val likkaEnabled: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val aiEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    // Package names, not TargetApp: an added app (RF-S06) is only known by its package.
    val watchedPackages: Set<String> = EscalationConfig.DEFAULT_TARGET_PACKAGES.keys,
    val addedPackages: Set<String> = emptySet(),
    val miuiConfirmed: Set<MiuiTask> = emptySet(),
    val pausedUntilMillis: Long? = null,
)

/**
 * Daily counters of RF-D01 plus the streak of RF-D04. The counters belong to [date] (`today_date`)
 * and go back to zero when the day changes; [streakDays] and [lastStreakDate] survive the change.
 */
data class DailyStats(
    val date: LocalDate? = null,
    val usageMinutes: Int = 0,
    val interventions: Int = 0,
    val level3Count: Int = 0,
    val pauses: Int = 0,
    // True once Likka was off at any moment of [date]; such a day does not add to the streak.
    val likkaDisabledToday: Boolean = false,
    val streakDays: Int = 0,
    val lastStreakDate: LocalDate? = null,
)

/** Everything the stats store holds, already rolled over to the current day. */
data class LikkaSnapshot(
    val settings: LikkaSettings = LikkaSettings(),
    val today: DailyStats = DailyStats(),
) {
    fun isPausedAt(nowEpochMillis: Long): Boolean = settings.pausedUntilMillis?.let { it > nowEpochMillis } == true
}
