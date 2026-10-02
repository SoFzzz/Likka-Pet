package com.likkapet.presentation.components

import com.likkapet.domain.model.MotionPose
import org.junit.Assert.assertEquals
import org.junit.Test

/** The overlay draws the planner's poses through [LikkaPose.fromKey]; none may fall back to `idle` silently. */
class LikkaPoseKeysTest {
    @Test
    fun `every planner pose has a LikkaPose with the same key`() {
        MotionPose.entries.forEach { pose -> assertEquals(pose.key, LikkaPose.fromKey(pose.key).key) }
    }

    @Test
    fun `an unknown key falls back to idle`() {
        assertEquals(LikkaPose.IDLE, LikkaPose.fromKey("no_such_pose"))
    }
}
