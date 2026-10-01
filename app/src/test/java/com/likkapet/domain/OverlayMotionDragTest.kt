package com.likkapet.domain

import com.likkapet.domain.model.MotionMode
import com.likkapet.domain.model.MotionPose
import com.likkapet.domain.model.OverlayEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4b, RF-O11 and RF-O13: while dragged the window follows the finger on the sprite-pixel grid
 * and never over the system bars, and the full gesture (down, moves, up) ends the way §9.6 says.
 */
class OverlayMotionDragTest {
    private val harness = PlannerHarness()
    private val gridPx = PlannerHarness.PHONE.spritePixelPx

    @Test
    fun `while dragged the window follows the finger on the sprite pixel grid`() {
        harness.showLevel(2)
        harness.planner.onDragStarted()

        harness.planner.onDragMoved(301, 502)

        assertEquals(MotionMode.DRAGGED, harness.motion.mode)
        assertEquals(MotionPose.DRAGGED, harness.motion.pose)
        assertEquals(0, (harness.motion.x - harness.area.minX) % gridPx)
        assertEquals(0, (harness.motion.y - harness.area.minY) % gridPx)
        assertTrue(harness.motion.x in 299..301 && harness.motion.y in 500..502)
    }

    @Test
    fun `dragging over the status bar or the navigation bar stops at the safe area`() {
        harness.showLevel(2)
        harness.planner.onDragStarted()

        harness.planner.onDragMoved(-400, -400)
        harness.assertInsideSafeArea(harness.motion)
        assertEquals(harness.area.minY, harness.motion.y)

        harness.planner.onDragMoved(5_000, 5_000)
        harness.assertInsideSafeArea(harness.motion)
    }

    @Test
    fun `a move without a drag is ignored`() {
        harness.showLevel(2)
        val before = harness.motion

        harness.planner.onDragMoved(10, 10)

        assertEquals(before, harness.motion)
    }

    @Test
    fun `a rest cannot be moved`() {
        harness.showLevel(1)
        harness.resolveWithFarewell()
        val resting = harness.motion
        harness.planner.onDragStarted()

        harness.planner.onDragMoved(300, 300)

        assertEquals(resting, harness.motion)
    }

    @Test
    fun `level 1 dragged across the screen sticks to the nearest edge when let go`() {
        harness.showLevel(1)
        harness.planner.onDragStarted()
        harness.planner.onDragMoved(300, 600)
        harness.planner.onDragMoved(harness.area.maxX - 10, 650)

        harness.planner.onDragEnded(harness.area.maxX - 10, 650)

        assertEquals(MotionMode.ON_EDGE, harness.motion.mode)
        assertEquals(OverlayEdge.RIGHT, harness.motion.edge)
        assertEquals(harness.area.maxX, harness.motion.x)
    }

    @Test
    fun `level 2 dragged and let go walks back to the central zone after the return delay`() {
        harness.showLevel(2)
        harness.planner.onDragStarted()
        harness.planner.onDragMoved(0, 0)
        harness.planner.onDragEnded(0, 0)
        val dropped = harness.motion

        harness.advanceBy(seconds = EscalationConfig.LEVEL_2_RETURN_DELAY_SEC - 1, millis = 900)
        assertEquals(MotionMode.WAITING_TO_RETURN, harness.motion.mode)
        assertEquals(dropped, harness.motion)
        harness.advanceBy(millis = 100)
        assertEquals(MotionMode.WALKING, harness.motion.mode)
        harness.advanceBy(millis = PlannerHarness.STEP_MS)
        assertTrue(harness.motion.x != dropped.x || harness.motion.y != dropped.y)
    }

    @Test
    fun `the timers do not run while dragged at level 2`() {
        harness.showLevel(2)
        harness.planner.onDragStarted()
        harness.planner.onDragMoved(200, 400)
        val held = harness.motion

        harness.advanceBy(seconds = 30)

        assertEquals(held, harness.motion)
    }
}
