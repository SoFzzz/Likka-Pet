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
    val FAREWELL_MS = EscalationConfig.FAREWELL_SEC.seconds.inWholeMilliseconds
    val MILLIS_PER_SECOND = 1.seconds.inWholeMilliseconds
    val MILLIS_PER_MINUTE = 1.minutes.inWholeMilliseconds
}
