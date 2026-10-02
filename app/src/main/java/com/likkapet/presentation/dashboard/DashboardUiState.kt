package com.likkapet.presentation.dashboard

import androidx.compose.runtime.Immutable
import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.PauseRules
import com.likkapet.domain.model.LikkaSnapshot
import com.likkapet.presentation.components.LikkaPose
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

/**
 * Signals that come from the service and the Worker, not from the stats store: the Level 3 flag
 * (from the running coordinator, via `PauseController`) and the connectivity pair (what the last
 * Worker answers say, `AiServiceStatus`).
 */
@Immutable
data class RuntimeSignals(
    val isLevel3Active: Boolean = false,
    val isOnline: Boolean = true,
    val hasAiCredit: Boolean = true,
)

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
    snapshot: LikkaSnapshot,
    hasAllPermissions: Boolean,
    signals: RuntimeSignals,
    nowMillis: Long,
): DashboardStatus =
    when {
        !snapshot.settings.likkaEnabled -> DashboardStatus.DISABLED
        !hasAllPermissions -> DashboardStatus.PERMISSION_MISSING
        snapshot.isPausedAt(nowMillis) -> DashboardStatus.PAUSED
        !snapshot.settings.aiEnabled -> DashboardStatus.AI_DISABLED
        !signals.isOnline || !signals.hasAiCredit -> DashboardStatus.OFFLINE
        else -> DashboardStatus.PROTECTING
    }

fun buildDashboardUiState(
    snapshot: LikkaSnapshot,
    hasAllPermissions: Boolean,
    signals: RuntimeSignals,
    local: DashboardLocalState,
    nowMillis: Long,
    zone: ZoneId,
): DashboardUiState =
    DashboardUiState(
        status = deriveDashboardStatus(snapshot, hasAllPermissions, signals, nowMillis),
        pausedUntilLabel = snapshot.settings.pausedUntilMillis?.let { formatClockTime(it, zone) },
        minutesToday = snapshot.today.usageMinutes,
        interventionsToday = snapshot.today.interventions,
        streakDays = snapshot.today.streakDays,
        pausesLeft = PauseRules.pausesLeft(snapshot.today.pauses),
        pauseAvailability = pauseAvailability(snapshot, signals),
        pauseOptionsMinutes = EscalationConfig.PAUSE_OPTIONS_MIN,
        local = local,
    )

private fun pauseAvailability(
    snapshot: LikkaSnapshot,
    signals: RuntimeSignals,
): PauseAvailability =
    when {
        signals.isLevel3Active -> PauseAvailability.LEVEL_3_ACTIVE
        PauseRules.pausesLeft(snapshot.today.pauses) == 0 -> PauseAvailability.NO_PAUSES_LEFT
        else -> PauseAvailability.AVAILABLE
    }

private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatClockTime(
    epochMillis: Long,
    zone: ZoneId,
): String = clockFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
