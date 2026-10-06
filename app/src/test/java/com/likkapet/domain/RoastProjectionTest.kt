package com.likkapet.domain

import com.likkapet.domain.model.TriggerReason
import org.junit.Assert.assertEquals
import org.junit.Test

/** RF-I11: a prefetch carries the values at which its level fires. */
class RoastProjectionTest {
    @Test
    fun `level 1 of USAGE_TIME projects the usage threshold`() {
        assertEquals(EscalationConfig.USAGE_THRESHOLD_MIN, RoastProjection.minutes(1, TriggerReason.USAGE_TIME))
    }

    @Test
    fun `later levels add the time the escalation timers take`() {
        val level1 = EscalationConfig.USAGE_THRESHOLD_MIN
        assertEquals(level1 + EscalationConfig.LEVEL_1_TO_2_MIN, RoastProjection.minutes(2, TriggerReason.USAGE_TIME))
        assertEquals(
            level1 + EscalationConfig.LEVEL_1_TO_2_MIN + EscalationConfig.LEVEL_2_TO_3_MIN,
            RoastProjection.minutes(3, TriggerReason.USAGE_TIME),
        )
    }

    @Test
    fun `POSTURE projects the danger angle at every level`() {
        (1..3).forEach { assertEquals(EscalationConfig.POSTURE_DANGER_ANGLE.toInt(), RoastProjection.angle(TriggerReason.POSTURE)) }
    }

    @Test
    fun `the field that does not belong to the reason is a neutral filler`() {
        assertEquals(0, RoastProjection.minutes(1, TriggerReason.POSTURE))
        assertEquals(EscalationConfig.POSTURE_RESET_ANGLE.toInt(), RoastProjection.angle(TriggerReason.USAGE_TIME))
    }
}
