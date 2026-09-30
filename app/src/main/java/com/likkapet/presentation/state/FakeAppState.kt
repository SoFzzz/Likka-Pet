package com.likkapet.presentation.state

import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.ThemeMode

/** Permissions the onboarding checklist and "Revisar permisos" show (RF-A04). */
enum class AppPermission { NOTIFICATIONS, OVERLAY, USAGE_STATS }

enum class PermissionStatus { PENDING, GRANTED, DENIED }

/** The three MIUI settings the guide asks for (RF-A05); the app cannot read them, so the user confirms. */
enum class MiuiTask { AUTOSTART, BATTERY, POPUP }

/**
 * Everything the screens read and write, held in memory until `data/` provides DataStore and
 * `StatsStore` (a later task). Field names follow the DataStore keys of RF-D01 so the swap is
 * mechanical.
 */
data class FakeAppState(
    val onboardingCompleted: Boolean = false,
    val likkaEnabled: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val aiEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val watchedApps: Set<TargetApp> = TargetApp.entries.toSet(),
    val permissions: Map<AppPermission, PermissionStatus> = emptyMap(),
    val miuiConfirmed: Set<MiuiTask> = emptySet(),
    val pausedUntilMillis: Long? = null,
    val pausesToday: Int = 0,
    val isLevel3Active: Boolean = false,
    val isOnline: Boolean = true,
    val hasAiCredit: Boolean = true,
    val minutesToday: Int = SAMPLE_MINUTES_TODAY,
    val interventionsToday: Int = SAMPLE_INTERVENTIONS_TODAY,
    val streakDays: Int = SAMPLE_STREAK_DAYS,
) {
    val hasAllPermissions: Boolean
        get() = permissions.values.all { it == PermissionStatus.GRANTED }

    val pausesLeft: Int
        get() = (EscalationConfig.MAX_PAUSES_PER_DAY - pausesToday).coerceAtLeast(0)

    fun isPausedAt(nowMillis: Long): Boolean = pausedUntilMillis?.let { it > nowMillis } == true

    private companion object {
        const val SAMPLE_MINUTES_TODAY = 23
        const val SAMPLE_INTERVENTIONS_TODAY = 4
        const val SAMPLE_STREAK_DAYS = 6
    }
}

/** All permissions pending, in checklist order; notifications only where the OS asks for them (API 33+). */
fun pendingPermissions(includeNotifications: Boolean): Map<AppPermission, PermissionStatus> =
    AppPermission.entries
        .filter { includeNotifications || it != AppPermission.NOTIFICATIONS }
        .associateWith { PermissionStatus.PENDING }
