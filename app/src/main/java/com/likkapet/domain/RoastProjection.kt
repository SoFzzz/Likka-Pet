package com.likkapet.domain

import com.likkapet.domain.model.TriggerReason

/**
 * The `minutes` and `angle` a prefetched roast is asked for (documentación §6 Módulo 4, RF-I11).
 * A prefetch happens before its level is reached, so it carries the value at which that level
 * would fire, never a live reading that would be stale by the time the roast is shown.
 *
 * Only the field that belongs to the reason is meaningful; the other one is a neutral filler the
 * Worker requires but its prompt ignores for that reason: no session minutes for a posture roast,
 * and an angle in the healthy range for a usage roast.
 */
object RoastProjection {
    fun minutes(
        level: Int,
        reason: TriggerReason,
    ): Int =
        when (reason) {
            TriggerReason.POSTURE -> 0
            TriggerReason.USAGE_TIME -> EscalationConfig.USAGE_THRESHOLD_MIN + minutesAddedByEscalation(level)
        }

    fun angle(reason: TriggerReason): Int =
        when (reason) {
            TriggerReason.POSTURE -> EscalationConfig.POSTURE_DANGER_ANGLE.toInt()
            TriggerReason.USAGE_TIME -> EscalationConfig.POSTURE_RESET_ANGLE.toInt()
        }

    // Level 2 starts LEVEL_1_TO_2_MIN after level 1, and level 3 LEVEL_2_TO_3_MIN after level 2.
    private fun minutesAddedByEscalation(level: Int): Int =
        when {
            level >= 3 -> EscalationConfig.LEVEL_1_TO_2_MIN + EscalationConfig.LEVEL_2_TO_3_MIN
            level == 2 -> EscalationConfig.LEVEL_1_TO_2_MIN
            else -> 0
        }
}
