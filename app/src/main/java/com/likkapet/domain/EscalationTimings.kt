package com.likkapet.domain

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** [EscalationConfig] values converted once to milliseconds, the unit of [com.likkapet.domain.port.Clock]. */
internal object EscalationTimings {
    val POSTURE_TRIGGER_MS = EscalationConfig.POSTURE_TRIGGER_SEC.seconds.inWholeMilliseconds
    val POSTURE_RESET_MS = EscalationConfig.POSTURE_RESET_SEC.seconds.inWholeMilliseconds
    val USAGE_THRESHOLD_MS = EscalationConfig.USAGE_THRESHOLD_MIN.minutes.inWholeMilliseconds
    val SESSION_END_AWAY_MS = EscalationConfig.SESSION_END_AWAY_MIN.minutes.inWholeMilliseconds
    val LEVEL_1_TO_2_MS = EscalationConfig.LEVEL_1_TO_2_MIN.minutes.inWholeMilliseconds
    val LEVEL_2_TO_3_MS = EscalationConfig.LEVEL_2_TO_3_MIN.minutes.inWholeMilliseconds
    val LEVEL_3_AUTO_HOME_MS = EscalationConfig.LEVEL_3_AUTO_HOME_SEC.seconds.inWholeMilliseconds
    val GRACE_AFTER_RESET_MS = EscalationConfig.GRACE_AFTER_RESET_SEC.seconds.inWholeMilliseconds
    val RELAPSE_WINDOW_MS = EscalationConfig.RELAPSE_WINDOW_MIN.minutes.inWholeMilliseconds
    val QUICK_RETURN_WINDOW_MS = EscalationConfig.QUICK_RETURN_WINDOW_MIN.minutes.inWholeMilliseconds
    val EJECTION_EXIT_TIMEOUT_MS = EscalationConfig.EJECTION_EXIT_TIMEOUT_SEC.seconds.inWholeMilliseconds
    val CALL_END_GRACE_MS = EscalationConfig.CALL_END_GRACE_SEC.seconds.inWholeMilliseconds
    val FAREWELL_MS = EscalationConfig.FAREWELL_SEC.seconds.inWholeMilliseconds
    val LEVEL_1_HOP_INTERVAL_MS = EscalationConfig.LEVEL_1_HOP_INTERVAL_SEC.seconds.inWholeMilliseconds
    val LEVEL_2_WALK_MIN_MS = EscalationConfig.LEVEL_2_WALK_MIN_SEC.seconds.inWholeMilliseconds
    val LEVEL_2_STOP_MS = EscalationConfig.LEVEL_2_STOP_SEC.seconds.inWholeMilliseconds
    val LEVEL_2_RETURN_DELAY_MS = EscalationConfig.LEVEL_2_RETURN_DELAY_SEC.seconds.inWholeMilliseconds
    val MOVEMENT_STEP_MS = 1.seconds.inWholeMilliseconds / EscalationConfig.MOVEMENT_STEP_FPS
    val POKE_REACTION_WINDOW_MS = EscalationConfig.POKE_REACTION_WINDOW_SEC.seconds.inWholeMilliseconds
    val LOCAL_REACTION_MS = EscalationConfig.LOCAL_REACTION_SEC.seconds.inWholeMilliseconds
    val USAGE_FLUSH_MS = EscalationConfig.USAGE_FLUSH_SEC.seconds.inWholeMilliseconds
    val MILLIS_PER_SECOND = 1.seconds.inWholeMilliseconds
    val MILLIS_PER_MINUTE = 1.minutes.inWholeMilliseconds

    init {
        // OverlayMotionPlanner's time slicing needs a positive step to make progress.
        require(MOVEMENT_STEP_MS > 0) { "MOVEMENT_STEP_FPS must be at most 1000" }
    }
}
