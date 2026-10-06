package com.likkapet.domain

import com.likkapet.domain.model.MotionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 4b, RNF-R05: the service only wakes up when the movement can change. `msUntilNextChange`
 * says when, and says nothing at all while nothing can change on its own.
 */
class OverlayMotionWakeUpTest {
    private val harness = PlannerHarness()

    @Test
    fun `level 1 sleeps until its next hop`() {
        harness.showLevel(1)
        assertEquals(EscalationConfig.LEVEL_1_HOP_INTERVAL_SEC * 1_000L, harness.planner.msUntilNextChange())

        harness.clock.nowMs += 15_000

        assertEquals(EscalationConfig.LEVEL_1_HOP_INTERVAL_SEC * 1_000L - 15_000, harness.planner.msUntilNextChange())
    }

    @Test
    fun `ticking when told hops exactly at the interval`() {
        harness.showLevel(1)
        val start = harness.motion

        harness.clock.nowMs += checkNotNull(harness.planner.msUntilNextChange())
        harness.planner.onTick()

        assertEquals(MotionMode.ON_EDGE, harness.motion.mode)
        assertEquals(EscalationConfig.LEVEL_1_HOP_INTERVAL_SEC * 1_000L, harness.clock.nowMs)
        assert(harness.motion != start)
    }

    @Test
    fun `walking wakes up every movement step`() {
        harness.showLevel(2)

        assertEquals(EscalationTimings.MOVEMENT_STEP_MS, harness.planner.msUntilNextChange())
    }

    @Test
    fun `stopped level 2 sleeps until it walks again`() {
        harness.showLevel(2)
        while (harness.motion.mode == MotionMode.WALKING) {
            harness.advanceBy(millis = PlannerHarness.STEP_MS)
        }

        assertEquals(MotionMode.STOPPED, harness.motion.mode)
        assertEquals(EscalationConfig.LEVEL_2_STOP_SEC * 1_000L, harness.planner.msUntilNextChange())
    }

    @Test
    fun `after a drop it sleeps until the return delay`() {
        harness.showLevel(2)
        harness.dragTo(0, 0)

        assertEquals(EscalationConfig.LEVEL_2_RETURN_DELAY_SEC * 1_000L, harness.planner.msUntilNextChange())
    }

    @Test
    fun `nothing to wait for while hidden, dragged, resting or with animations off`() {
        harness.showLevel(2)
        harness.hide()
        assertNull(harness.planner.msUntilNextChange())

        harness.show()
        harness.planner.onDragStarted()
        assertNull(harness.planner.msUntilNextChange())
        harness.planner.onDragEnded(0, 0)

        harness.planner.onAnimationsEnabledChanged(false)
        assertNull(harness.planner.msUntilNextChange())
        harness.planner.onAnimationsEnabledChanged(true)

        harness.resolveWithFarewell()
        assertNull(harness.planner.msUntilNextChange())
    }
}
