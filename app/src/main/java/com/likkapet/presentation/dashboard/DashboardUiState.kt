package com.likkapet.presentation.dashboard

import androidx.compose.runtime.Immutable
import com.likkapet.domain.EscalationConfig
import com.likkapet.presentation.components.LikkaPose
import com.likkapet.presentation.state.FakeAppState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The six global states of design system §3.5, each with its Likka pose. */
enum class DashboardStatus(
    val pose: LikkaPose,
) {
    PROTECTING(LikkaPose.IDLE),
    PAUSED(LikkaPose.SIT),
    DISABLED(LikkaPose.SLEEPING),
    PERMISSION_MISSING(LikkaPose.WORRIED),
    OFFLINE(LikkaPose.IDLE),
    AI_DISABLED(LikkaPose.IDLE),
}

/** Why the pause button is or is not usable (design system §2.4). */
enum class PauseAvailability { AVAILABLE, NO_PAUSES_LEFT, LEVEL_3_ACTIVE }

/** Transient dashboard UI (sheet, dialog) that is not part of the app state. */
@Immutable
data class DashboardLocalState(
    val isPauseSheetVisible: Boolean = false,
    val selectedPauseMinutes: Int = DEFAULT_PAUSE_MINUTES,
    val isDeactivateDialogVisible: Boolean = false,
) {
    companion object {
        // 30 min is preselected (design system §2.4); it is one of PAUSE_OPTIONS_MIN.
        val DEFAULT_PAUSE_MINUTES = EscalationConfig.NOTIFICATION_PAUSE_MIN
    }
}

@Immutable
data class DashboardUiState(
    val status: DashboardStatus,
    val pausedUntilLabel: String?,
    val minutesToday: Int,
    val interventionsToday: Int,
    val streakDays: Int,
    val pausesLeft: Int,
    val pauseAvailability: PauseAvailability,
    val pauseOptionsMinutes: List<Int>,
    val local: DashboardLocalState,
) {
    companion object {
        fun preview(status: DashboardStatus = DashboardStatus.PROTECTING) =
            DashboardUiState(
                status = status,
                pausedUntilLabel = "18:40".takeIf { status == DashboardStatus.PAUSED },
                minutesToday = 23,
                interventionsToday = 4,
                streakDays = 6,
                pausesLeft = EscalationConfig.MAX_PAUSES_PER_DAY,
                pauseAvailability = PauseAvailability.AVAILABLE,
                pauseOptionsMinutes = EscalationConfig.PAUSE_OPTIONS_MIN,
                local = DashboardLocalState(),
            )
    }
}

/**
 * Picks the global state. Order matters: a disabled Likka hides everything else; a missing
 * permission beats a pause (Likka cannot appear either way); the connectivity notice only
 * exists while AI is on, because switching AI off is the user's choice, not an error (§3.5).
 */
fun deriveDashboardStatus(
    state: FakeAppState,
    nowMillis: Long,
): DashboardStatus =
    when {
        !state.likkaEnabled -> DashboardStatus.DISABLED
        !state.hasAllPermissions -> DashboardStatus.PERMISSION_MISSING
        state.isPausedAt(nowMillis) -> DashboardStatus.PAUSED
        !state.aiEnabled -> DashboardStatus.AI_DISABLED
        !state.isOnline || !state.hasAiCredit -> DashboardStatus.OFFLINE
        else -> DashboardStatus.PROTECTING
    }

fun buildDashboardUiState(
    state: FakeAppState,
    local: DashboardLocalState,
    nowMillis: Long,
    zone: ZoneId,
): DashboardUiState =
    DashboardUiState(
        status = deriveDashboardStatus(state, nowMillis),
        pausedUntilLabel = state.pausedUntilMillis?.let { formatClockTime(it, zone) },
        minutesToday = state.minutesToday,
        interventionsToday = state.interventionsToday,
        streakDays = state.streakDays,
        pausesLeft = state.pausesLeft,
        pauseAvailability = pauseAvailability(state),
        pauseOptionsMinutes = EscalationConfig.PAUSE_OPTIONS_MIN,
        local = local,
    )

private fun pauseAvailability(state: FakeAppState): PauseAvailability =
    when {
        state.isLevel3Active -> PauseAvailability.LEVEL_3_ACTIVE
        state.pausesLeft == 0 -> PauseAvailability.NO_PAUSES_LEFT
        else -> PauseAvailability.AVAILABLE
    }

private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatClockTime(
    epochMillis: Long,
    zone: ZoneId,
): String = clockFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
