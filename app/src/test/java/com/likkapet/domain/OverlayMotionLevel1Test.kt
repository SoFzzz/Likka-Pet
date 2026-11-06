package com.likkapet.domain

import com.likkapet.domain.model.MotionMode
import com.likkapet.domain.model.MotionPose
import com.likkapet.domain.model.OverlayEdge
import com.likkapet.domain.model.OverlayMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** RF-O09 and §12.1 "OverlayMotionPlanner": Level 1 hops, peek/perch and the drop on an edge. */
class OverlayMotionLevel1Test {
    private val harness = PlannerHarness()

    @Test
    fun `entering level 1 places Likka flush on an edge inside the safe area`() {
        harness.showLevel(1)
        assertEquals(MotionMode.ON_EDGE, harness.motion.mode)
        assertFlushOnItsEdge(harness, harness.motion)
    }

    @Test
    fun `position holds until the hop interval`() {
        harness.showLevel(1)
        val start = harness.motion
        harness.advanceBy(seconds = HOP_SEC - 1, millis = 900)
        assertEquals(start, harness.motion)
    }

    @Test
    fun `every hop interval moves to a distinct edge position`() {
        repeat(SEEDS) { seed ->
            val run = PlannerHarness(seed)
            run.showLevel(1)
            repeat(HOPS_PER_SEED) {
                val before = run.motion
                run.advanceBy(seconds = HOP_SEC)
                val after = run.motion
                assertTrue("seed $seed: $after not distinct from $before", isDistinct(run, after, before))
                assertFlushOnItsEdge(run, after)
            }
        }
    }

    @Test
    fun `bottom edge perches above the navigation bar, side edges peek`() {
        val seen = mutableSetOf<OverlayEdge>()
        repeat(SEEDS) { seed ->
            val run = PlannerHarness(seed)
            run.showLevel(1)
            repeat(HOPS_PER_SEED) {
                val motion = run.motion
                val edge = checkNotNull(motion.edge)
                seen += edge
                if (edge == OverlayEdge.BOTTOM) {
                    assertEquals(MotionPose.PERCH, motion.pose)
                    val navigationBarTop = run.geometry.screenHeightPx - run.geometry.insets.bottom
                    assertEquals(navigationBarTop, motion.y + run.geometry.windowHeightPx)
                } else {
                    assertEquals(MotionPose.PEEK, motion.pose)
                }
                run.advanceBy(seconds = HOP_SEC)
            }
        }
        assertEquals(OverlayEdge.entries.toSet(), seen)
    }

    @Test
    fun `a drag shows the dragged pose without an edge`() {
        harness.showLevel(1)
        harness.planner.onDragStarted()
        assertEquals(MotionPose.DRAGGED, harness.motion.pose)
        assertEquals(MotionMode.DRAGGED, harness.motion.mode)
        assertNull(harness.motion.edge)
    }

    @Test
    fun `dropping near the left edge snaps to it on the grid`() {
        harness.showLevel(1)
        harness.dragTo(x = 40, y = 701)
        assertEquals(OverlayEdge.LEFT, harness.motion.edge)
        assertEquals(harness.area.minX, harness.motion.x)
        assertOnGrid(harness.motion.y - harness.area.minY)
        assertTrue(harness.motion.y in 698..701)
    }

    @Test
    fun `dropping near the right edge snaps to it on the grid`() {
        harness.showLevel(1)
        harness.dragTo(x = harness.area.maxX - 30, y = 500)
        assertEquals(OverlayEdge.RIGHT, harness.motion.edge)
        assertEquals(harness.area.maxX, harness.motion.x)
        assertOnGrid(harness.motion.y - harness.area.minY)
    }

    @Test
    fun `dropping near the bottom edge perches on it on the grid`() {
        harness.showLevel(1)
        harness.dragTo(x = 250, y = harness.area.maxY - 20)
        assertEquals(OverlayEdge.BOTTOM, harness.motion.edge)
        assertEquals(MotionPose.PERCH, harness.motion.pose)
        assertEquals(harness.area.maxY, harness.motion.y)
        assertOnGrid(harness.motion.x - harness.area.minX)
    }

    @Test
    fun `dropping off screen or over the status bar still lands inside the safe area`() {
        harness.showLevel(1)
        harness.dragTo(x = -500, y = -500)
        assertEquals(OverlayEdge.LEFT, harness.motion.edge)
        assertFlushOnItsEdge(harness, harness.motion)
        harness.dragTo(x = 5_000, y = 5_000)
        assertFlushOnItsEdge(harness, harness.motion)
    }

    @Test
    fun `a drop does not reset the hop timer`() {
        harness.showLevel(1)
        harness.advanceBy(seconds = 20)
        harness.dragTo(x = 40, y = 700)
        val dropped = harness.motion
        harness.advanceBy(seconds = HOP_SEC - 20 - 1, millis = 900)
        assertEquals(dropped, harness.motion)
        harness.advanceBy(millis = 100)
        assertTrue(isDistinct(harness, harness.motion, dropped))
    }

    @Test
    fun `a hop due while dragged waits for the drop`() {
        harness.showLevel(1)
        harness.advanceBy(seconds = 30)
        harness.planner.onDragStarted()
        harness.advanceBy(seconds = 30)
        assertEquals(MotionMode.DRAGGED, harness.motion.mode)
        harness.planner.onDragEnded(40, 700)
        val dropped = harness.motion
        assertEquals(OverlayEdge.LEFT, dropped.edge)
        harness.advanceBy(millis = 100)
        assertTrue(isDistinct(harness, harness.motion, dropped))
    }

    @Test
    fun `a bigger window stays flush on the right edge`() {
        harness.showLevel(1)
        harness.dragTo(x = 700, y = 500)
        harness.changeGeometry(PlannerHarness.PHONE.copy(windowWidthPx = 300))
        assertEquals(OverlayEdge.RIGHT, harness.motion.edge)
        assertFlushOnItsEdge(harness, harness.motion)
    }

    // Redmi 9 bug (2026-11): a tap unfolded the roast above Likka, the window grew downward and he
    // jumped ~250 px down, then back up when it folded, so the next tap missed him.
    @Test
    fun `unfolding the roast on a side edge keeps Likka's feet in place`() {
        harness.showLevel(1)
        harness.dragTo(x = 0, y = 600)
        val folded = harness.motion
        val feetY = folded.y + harness.geometry.windowHeightPx

        harness.changeGeometry(PlannerHarness.PHONE.copy(windowHeightPx = PlannerHarness.PHONE.windowHeightPx + 252))

        assertEquals(OverlayEdge.LEFT, harness.motion.edge)
        assertEquals(feetY, harness.motion.y + harness.geometry.windowHeightPx)
        harness.changeGeometry(PlannerHarness.PHONE)
        assertEquals(folded, harness.motion)
    }

    @Test
    fun `repeated unfolds off the sprite-pixel grid never drift`() {
        harness.showLevel(1)
        harness.dragTo(x = 0, y = 600)
        val folded = harness.motion
        val feetY = folded.y + harness.geometry.windowHeightPx
        val unfolded = PlannerHarness.PHONE.copy(windowHeightPx = PlannerHarness.PHONE.windowHeightPx + 250)

        repeat(5) {
            harness.changeGeometry(unfolded)
            val unfoldedFeetY = harness.motion.y + unfolded.windowHeightPx
            assertTrue("feet moved to $unfoldedFeetY from $feetY", abs(unfoldedFeetY - feetY) < unfolded.spritePixelPx)
            harness.changeGeometry(PlannerHarness.PHONE)
            assertEquals(folded, harness.motion)
        }
    }

    // The safe-area clamp near the top eats part of the upward shift; the fold must give it back.
    @Test
    fun `unfolding near the top clamps, and the fold returns to the original spot`() {
        harness.showLevel(1)
        harness.dragTo(x = 0, y = harness.area.minY + 100)
        val folded = harness.motion
        val unfolded = PlannerHarness.PHONE.copy(windowHeightPx = PlannerHarness.PHONE.windowHeightPx + 252)

        harness.changeGeometry(unfolded)
        assertEquals(harness.area.minY, harness.motion.y)
        harness.changeGeometry(PlannerHarness.PHONE)
        assertEquals(folded, harness.motion)

        harness.changeGeometry(unfolded)
        harness.changeGeometry(PlannerHarness.PHONE)
        assertEquals(folded, harness.motion)
    }

    @Test
    fun `a screen change does not apply the feet shift`() {
        harness.showLevel(1)
        harness.dragTo(x = 0, y = 600)
        val before = harness.motion
        val insetsChanged =
            PlannerHarness.PHONE.copy(
                insets = PlannerHarness.PHONE.insets.copy(bottom = 100),
                windowHeightPx = PlannerHarness.PHONE.windowHeightPx + 252,
            )

        harness.changeGeometry(insetsChanged)

        assertEquals(before.y, harness.motion.y)
    }

    @Test
    fun `the bottom edge stays pinned to the bottom of the safe area`() {
        harness.showLevel(1)
        harness.dragTo(x = 300, y = 1500)
        assertEquals(OverlayEdge.BOTTOM, harness.motion.edge)
        val unfolded = PlannerHarness.PHONE.copy(windowHeightPx = PlannerHarness.PHONE.windowHeightPx + 252)

        harness.changeGeometry(unfolded)
        assertEquals(harness.area.maxY, harness.motion.y)
        harness.changeGeometry(PlannerHarness.PHONE)
        assertEquals(harness.area.maxY, harness.motion.y)
    }

    @Test
    fun `a taller window while dragged leaves the window under the finger`() {
        harness.showLevel(1)
        harness.dragTo(x = 0, y = 600)
        harness.planner.onDragStarted()
        val grabbed = harness.motion

        harness.changeGeometry(PlannerHarness.PHONE.copy(windowHeightPx = PlannerHarness.PHONE.windowHeightPx + 252))

        assertEquals(grabbed, harness.motion)
    }

    // Written independently of EdgePlacement: a visible move is at least one window on either axis.
    private fun isDistinct(
        run: PlannerHarness,
        candidate: OverlayMotion,
        current: OverlayMotion,
    ): Boolean =
        abs(candidate.x - current.x) >= run.geometry.windowWidthPx ||
            abs(candidate.y - current.y) >= run.geometry.windowHeightPx

    @Test
    fun `promoted to level 2 while dragged, the drop starts the return delay`() {
        harness.showLevel(1)
        harness.planner.onDragStarted()
        harness.showLevel(2)
        val grabbed = harness.motion
        assertEquals(MotionMode.DRAGGED, grabbed.mode)
        harness.advanceBy(seconds = 10)
        assertEquals(grabbed, harness.motion)
        harness.planner.onDragEnded(300, 700)
        assertEquals(MotionMode.WAITING_TO_RETURN, harness.motion.mode)
    }

    @Test
    fun `a hop near a corner still moves at least one window`() {
        val cornerLeft = EdgePosition(OverlayEdge.LEFT, ScreenPoint(harness.area.minX, harness.area.maxY))
        val cornerBottom = EdgePosition(OverlayEdge.BOTTOM, ScreenPoint(harness.area.minX + 3, harness.area.maxY))
        assertTrue(!EdgePlacement.isDistinct(harness.area, cornerBottom, cornerLeft))
    }

    private fun assertFlushOnItsEdge(
        run: PlannerHarness,
        motion: OverlayMotion,
    ) {
        run.assertInsideSafeArea(motion)
        when (checkNotNull(motion.edge)) {
            OverlayEdge.LEFT -> assertEquals(run.area.minX, motion.x)
            OverlayEdge.RIGHT -> assertEquals(run.area.maxX, motion.x)
            OverlayEdge.BOTTOM -> assertEquals(run.area.maxY, motion.y)
        }
    }

    private fun assertOnGrid(offsetFromSafeCorner: Int) {
        assertEquals(0, offsetFromSafeCorner % PlannerHarness.PHONE.spritePixelPx)
    }

    private companion object {
        const val HOP_SEC = EscalationConfig.LEVEL_1_HOP_INTERVAL_SEC
        const val SEEDS = 20
        const val HOPS_PER_SEED = 10
    }
}
