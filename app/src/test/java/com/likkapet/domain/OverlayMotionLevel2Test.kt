package com.likkapet.domain

import com.likkapet.domain.model.MotionMode
import com.likkapet.domain.model.MotionPose
import com.likkapet.domain.model.OverlayEdge
import com.likkapet.domain.model.OverlayGeometry
import com.likkapet.domain.model.OverlayInsets
import com.likkapet.domain.model.OverlayMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** RF-O10, RF-O11 and §12.1 "OverlayMotionPlanner": Level 2 walk/stop cycle and the return after a drag. */
class OverlayMotionLevel2Test {
    private val harness = PlannerHarness()

    @Test
    fun `level 2 alternates walking and stopped in a cycle`() {
        harness.showLevel(2)
        repeat(3) {
            assertEquals(MotionMode.WALKING, harness.motion.mode)
            harness.advanceBy(seconds = WALK_SEC - 1, millis = 900)
            assertEquals(MotionMode.WALKING, harness.motion.mode)
            harness.advanceBy(millis = 100)
            assertEquals(MotionMode.STOPPED, harness.motion.mode)
            assertEquals(MotionPose.ANNOYED, harness.motion.pose)
            harness.advanceBy(seconds = STOP_SEC - 1, millis = 900)
            assertEquals(MotionMode.STOPPED, harness.motion.mode)
            harness.advanceBy(millis = 100)
        }
        assertEquals(MotionMode.WALKING, harness.motion.mode)
    }

    @Test
    fun `walking uses a walk pose and never peeks or perches`() {
        harness.showLevel(2)
        harness.advanceBy(seconds = 120).forEach { motion ->
            val shown = checkNotNull(motion)
            assertTrue(shown.mode != MotionMode.ON_EDGE)
            assertTrue(shown.pose != MotionPose.PEEK && shown.pose != MotionPose.PERCH)
            if (shown.mode == MotionMode.WALKING) assertTrue(shown.pose.key.startsWith("walk_"))
            harness.assertInsideSafeArea(shown)
        }
    }

    @Test
    fun `each step moves one sprite pixel on the dominant axis`() {
        harness.showLevel(2)
        val before = harness.motion
        harness.advanceBy(millis = PlannerHarness.STEP_MS)
        val after = harness.motion
        val stepPx = EscalationConfig.LEVEL_2_WALK_STEP_SPRITE_PX * PlannerHarness.PHONE.spritePixelPx
        assertEquals(stepPx, maxOf(abs(after.x - before.x), abs(after.y - before.y)))
    }

    @Test
    fun `level 2 starts walking from the level 1 position`() {
        harness.showLevel(1)
        val edgePosition = harness.motion
        harness.showLevel(2)
        assertEquals(MotionMode.WALKING, harness.motion.mode)
        assertEquals(edgePosition.x to edgePosition.y, harness.motion.x to harness.motion.y)
        assertNull(harness.motion.edge)
    }

    @Test
    fun `back to level 1 hops onto an edge`() {
        harness.showLevel(2)
        harness.advanceBy(seconds = 10)
        harness.showLevel(1)
        assertEquals(MotionMode.ON_EDGE, harness.motion.mode)
        harness.assertInsideSafeArea(harness.motion)
    }

    @Test
    fun `a drag freezes the cycle under the finger`() {
        harness.showLevel(2)
        harness.advanceBy(seconds = 2)
        harness.planner.onDragStarted()
        val grabbed = harness.motion
        harness.advanceBy(seconds = 30)
        assertEquals(grabbed, harness.motion)
        assertEquals(MotionPose.DRAGGED, grabbed.pose)
    }

    @Test
    fun `after a drop Likka waits the return delay, then walks back to the central zone and stops`() {
        harness.showLevel(2)
        harness.dragTo(x = 0, y = 0)
        val dropped = harness.motion
        assertEquals(MotionMode.WAITING_TO_RETURN, dropped.mode)
        assertEquals(MotionPose.ANNOYED, dropped.pose)
        harness.advanceBy(seconds = RETURN_SEC - 1, millis = 900)
        assertEquals(dropped, harness.motion)
        harness.advanceBy(millis = 100)
        assertEquals(MotionMode.WALKING, harness.motion.mode)

        // The way back is not capped at LEVEL_2_WALK_SEC: from the corner it takes longer.
        harness.advanceBy(seconds = WALK_SEC, millis = 100)
        assertEquals(MotionMode.WALKING, harness.motion.mode)

        val arrival = harness.advanceBy(seconds = 60).filterNotNull().first { it.mode != MotionMode.WALKING }
        assertEquals(MotionMode.STOPPED, arrival.mode)
        assertCenterInCentralZone(harness.geometry, arrival)
    }

    @Test
    fun `a drop outside the safe area is clamped into it, on the grid`() {
        harness.showLevel(2)
        harness.dragTo(x = -300, y = 4_000)
        harness.assertInsideSafeArea(harness.motion)
        assertEquals(harness.area.minX, harness.motion.x)
        assertTrue(harness.area.maxY - harness.motion.y < PlannerHarness.PHONE.spritePixelPx)
        assertEquals(0, (harness.motion.y - harness.area.minY) % PlannerHarness.PHONE.spritePixelPx)
    }

    @Test
    fun `back to level 1 while dragged, the drop snaps to an edge`() {
        harness.showLevel(2)
        harness.planner.onDragStarted()
        harness.showLevel(1)
        assertEquals(MotionMode.DRAGGED, harness.motion.mode)
        harness.planner.onDragEnded(harness.area.maxX - 10, 600)
        assertEquals(MotionMode.ON_EDGE, harness.motion.mode)
        assertEquals(OverlayEdge.RIGHT, harness.motion.edge)
    }

    @Test
    fun `a geometry change while walking re-aims and keeps the pose in line with the new heading`() {
        harness.showLevel(2)
        harness.advanceBy(seconds = 1)
        val rotated = OverlayGeometry(1600, 720, OverlayInsets(80, 72, 96, 0), 198, 240, 3)
        harness.changeGeometry(rotated)
        harness.assertInsideSafeArea(harness.motion)
        val start = harness.motion
        val walked = harness.advanceBy(seconds = 1).filterNotNull().last()
        val heading = WalkPlanning.walkPoseFor(walked.x - start.x, walked.y - start.y)
        assertEquals(heading, start.pose)
        harness.advanceBy(seconds = 60).forEach(harness::assertInsideSafeArea)
    }

    @Test
    fun `a window wider than the safe area is pinned to its left edge`() {
        val tooWide = PlannerHarness.PHONE.copy(windowWidthPx = 800)
        val run = PlannerHarness(initialGeometry = tooWide)
        run.showLevel(2)
        run.advanceBy(seconds = 30).forEach { assertEquals(tooWide.insets.left, checkNotNull(it).x) }
    }

    @Test
    fun `stop points have the window center in the central zone`() {
        repeat(200) { seed ->
            val target = WalkPlanning.centralZoneTarget(SafeArea(PlannerHarness.PHONE), Random(seed))
            assertCenterInCentralZone(PlannerHarness.PHONE, OverlayMotion(target.x, target.y, MotionPose.ANNOYED, MotionMode.STOPPED, null))
        }
    }

    @Test
    fun `on a narrow screen the target is still valid and inside the safe area`() {
        val narrow = OverlayGeometry(400, 900, OverlayInsets(0, 60, 0, 80), windowWidthPx = 390, windowHeightPx = 300, spritePixelPx = 4)
        val area = SafeArea(narrow)
        repeat(200) { seed ->
            val target = WalkPlanning.centralZoneTarget(area, Random(seed))
            assertTrue("$target", target.x in area.minX..area.maxX && target.y in area.minY..area.maxY)
        }
        val run = PlannerHarness(initialGeometry = narrow)
        run.showLevel(2)
        run.advanceBy(seconds = 60).forEach(run::assertInsideSafeArea)
    }

    @Test
    fun `a late tick replays the missed steps and the phase change`() {
        harness.showLevel(2)
        val start = harness.motion
        harness.jumpBy(seconds = WALK_SEC)
        assertEquals(MotionMode.STOPPED, harness.motion.mode)
        assertNotEquals(start.x to start.y, harness.motion.x to harness.motion.y)
    }

    private fun assertCenterInCentralZone(
        geometry: OverlayGeometry,
        motion: OverlayMotion,
    ) {
        val insets = geometry.insets
        val centerX = motion.x + geometry.windowWidthPx / 2
        val centerY = motion.y + geometry.windowHeightPx / 2
        assertTrue("x center $centerX", centerX in zone(insets.left, geometry.screenWidthPx - insets.right, geometry.spritePixelPx))
        assertTrue("y center $centerY", centerY in zone(insets.top, geometry.screenHeightPx - insets.bottom, geometry.spritePixelPx))
    }

    // Central zone of the safe area, widened by one grid cell for the snap to the sprite-pixel grid.
    private fun zone(
        safeStart: Int,
        safeEnd: Int,
        gridPx: Int,
    ): IntRange {
        val center = (safeStart + safeEnd) / 2
        val halfZone = ((safeEnd - safeStart) * EscalationConfig.LEVEL_2_CENTER_ZONE_FRACTION / 2).toInt()
        return (center - halfZone - gridPx)..(center + halfZone)
    }

    private companion object {
        const val WALK_SEC = EscalationConfig.LEVEL_2_WALK_SEC
        const val STOP_SEC = EscalationConfig.LEVEL_2_STOP_SEC
        const val RETURN_SEC = EscalationConfig.LEVEL_2_RETURN_DELAY_SEC
    }
}
