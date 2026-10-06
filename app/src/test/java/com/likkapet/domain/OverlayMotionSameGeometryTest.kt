package com.likkapet.domain

import com.likkapet.domain.model.MotionMode
import com.likkapet.domain.model.OverlayGeometry
import com.likkapet.domain.model.OverlayInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Redmi 9 bug (2026-10-05): Compose reported the same window size on every walking step, each one
 * reached the planner as a new geometry and every step started a new segment in another direction,
 * so Likka flipped poses without moving. The same geometry again must change nothing.
 */
class OverlayMotionSameGeometryTest {
    private val redmi = OverlayGeometry(1080, 2340, OverlayInsets(0, 76, 0, 130), 660, 750, 4)
    private val harness = PlannerHarness(initialGeometry = redmi)

    @Test
    fun `the same geometry on every step keeps one pose and keeps walking forward`() {
        harness.showLevel(2)
        val start = harness.motion
        val poses = mutableSetOf(start.pose)

        repeat(EscalationConfig.LEVEL_2_WALK_MIN_SEC * EscalationConfig.MOVEMENT_STEP_FPS) {
            harness.changeGeometry(redmi)
            harness.advanceBy(millis = PlannerHarness.STEP_MS)
            if (harness.motion.mode == MotionMode.WALKING) poses += harness.motion.pose
        }

        assertEquals(setOf(start.pose), poses)
        val moved = maxOf(kotlin.math.abs(harness.motion.x - start.x), kotlin.math.abs(harness.motion.y - start.y))
        assertTrue("moved only $moved px", moved >= EscalationConfig.LEVEL_2_WALK_MIN_SEC * EscalationConfig.MOVEMENT_STEP_FPS * 4 - 4)
    }

    @Test
    fun `the same geometry while stopped does not cut the pause short`() {
        harness.showLevel(2)
        while (harness.motion.mode != MotionMode.STOPPED) harness.advanceBy(millis = PlannerHarness.STEP_MS)
        val stopped = harness.motion

        repeat(EscalationConfig.LEVEL_2_STOP_SEC * EscalationConfig.MOVEMENT_STEP_FPS - 1) {
            harness.changeGeometry(redmi)
            harness.advanceBy(millis = PlannerHarness.STEP_MS)
        }

        assertEquals(stopped, harness.motion)
    }

    @Test
    fun `a really different geometry still re-fits`() {
        harness.showLevel(2)
        val smaller = redmi.copy(windowWidthPx = 400)

        harness.changeGeometry(smaller)

        harness.assertInsideSafeArea(harness.motion)
    }
}
