package com.likkapet.domain

import com.likkapet.domain.model.LikkaOverlayState
import com.likkapet.domain.model.MotionMode
import com.likkapet.domain.model.MotionPose
import com.likkapet.domain.model.OverlayEdge
import com.likkapet.domain.model.OverlayVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The level 0 gap of the planner: the farewell (RF-O04) and the pause still show Likka, so they get
 * a RESTING motion at the last Level 1–2 spot, or a fresh edge spot when there is none.
 */
class OverlayMotionRestingTest {
    private val harness = PlannerHarness()

    @Test
    fun `the farewell keeps the last level 1 spot and shows goodbye`() {
        harness.showLevel(1)
        val last = harness.motion
        harness.resolveWithFarewell()
        assertEquals(MotionMode.RESTING, harness.motion.mode)
        assertEquals(MotionPose.GOODBYE, harness.motion.pose)
        assertEquals(last.x to last.y, harness.motion.x to harness.motion.y)
        assertNull(harness.motion.edge)
    }

    @Test
    fun `the farewell keeps the last level 2 spot`() {
        harness.showLevel(2)
        harness.advanceBy(seconds = 2)
        val last = harness.motion
        harness.resolveWithFarewell()
        assertEquals(MotionPose.GOODBYE, harness.motion.pose)
        assertEquals(last.x to last.y, harness.motion.x to harness.motion.y)
    }

    @Test
    fun `a pause sits where Likka was`() {
        harness.showLevel(2)
        val last = harness.motion
        harness.pause()
        assertEquals(MotionMode.RESTING, harness.motion.mode)
        assertEquals(MotionPose.SIT, harness.motion.pose)
        assertEquals(last.x to last.y, harness.motion.x to harness.motion.y)
    }

    @Test
    fun `a rest never moves, however long it lasts`() {
        harness.showLevel(1)
        harness.pause()
        val resting = harness.motion
        val published = harness.advanceBy(seconds = EscalationConfig.LEVEL_1_HOP_INTERVAL_SEC * 3)
        published.forEach { assertEquals(resting, it) }
    }

    @Test
    fun `with no previous spot a rest is placed on an edge inside the safe area`() {
        PlannerHarness.TEST_SCREENS.forEach { geometry ->
            val run = PlannerHarness(initialGeometry = geometry)
            run.pause()
            assertEquals(MotionPose.SIT, run.motion.pose)
            run.assertInsideSafeArea(run.motion)
        }
    }

    @Test
    fun `the farewell after the level 3 panel gets a fresh spot`() {
        harness.showLevel(2)
        harness.showLevel(3)
        assertNull(harness.planner.motion.value)
        harness.resolveWithFarewell()
        assertEquals(MotionPose.GOODBYE, harness.motion.pose)
        harness.assertInsideSafeArea(harness.motion)
    }

    @Test
    fun `a rest waits for the geometry, like a level`() {
        val planner = OverlayMotionPlanner(FakeClock(), Random(PlannerHarness.DEFAULT_SEED))
        planner.onOverlayStateChanged(LikkaOverlayState.NOTHING_TO_SHOW.copy(isPaused = true))
        assertNull(planner.motion.value)
        planner.onGeometryChanged(PlannerHarness.PHONE)
        assertEquals(MotionPose.SIT, planner.motion.value?.pose)
    }

    @Test
    fun `a rest that began on an edge stays flush against it when the window shrinks`() {
        val run = restingOnASideEdge()
        val edge = checkNotNull(run.second)
        val smaller = run.first.geometry.copy(windowWidthPx = run.first.geometry.windowWidthPx / 2)
        run.first.changeGeometry(smaller)
        val motion = run.first.motion
        val area = run.first.area
        if (edge == OverlayEdge.RIGHT) assertEquals(area.maxX, motion.x) else assertEquals(area.minX, motion.x)
    }

    @Test
    fun `the end of the farewell clears the motion`() {
        harness.showLevel(1)
        harness.resolveWithFarewell()
        harness.showLevel(0)
        assertNull(harness.planner.motion.value)
    }

    @Test
    fun `the end of a pause back into level 1 hops away from the rest spot`() {
        harness.showLevel(1)
        harness.pause()
        val resting = harness.motion
        harness.showLevel(1)
        assertEquals(MotionMode.ON_EDGE, harness.motion.mode)
        assertTrue(resting.x != harness.motion.x || resting.y != harness.motion.y)
    }

    @Test
    fun `a pause while hidden keeps its spot for when it shows`() {
        harness.showLevel(1)
        val last = harness.motion
        harness.pause(visibility = OverlayVisibility.HIDDEN_WHILE_AWAY)
        assertEquals(last.x to last.y, harness.motion.x to harness.motion.y)
        assertEquals(MotionPose.SIT, harness.motion.pose)
    }

    @Test
    fun `a rest cannot be dragged`() {
        harness.showLevel(1)
        harness.pause()
        harness.dragTo(0, 0)
        assertEquals(MotionMode.RESTING, harness.motion.mode)
    }

    // A seed whose first Level 1 spot is on a side edge, so the rest inherits that edge.
    private fun restingOnASideEdge(): Pair<PlannerHarness, OverlayEdge?> {
        repeat(SEEDS) { seed ->
            val run = PlannerHarness(seed)
            run.showLevel(1)
            val edge = run.motion.edge
            if (edge == OverlayEdge.LEFT || edge == OverlayEdge.RIGHT) {
                run.pause()
                return run to edge
            }
        }
        error("No seed starts on a side edge")
    }

    private companion object {
        const val SEEDS = 50
    }
}
