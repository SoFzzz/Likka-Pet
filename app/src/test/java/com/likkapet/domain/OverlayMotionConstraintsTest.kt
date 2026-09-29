package com.likkapet.domain

import com.likkapet.domain.model.LikkaOverlayState
import com.likkapet.domain.model.MotionMode
import com.likkapet.domain.model.OverlayEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/**
 * RF-O13 (never inside the insets), RF-O14 (no movement with animations off) and the freeze while
 * hidden (RF-O07, §9.6): what holds for both moving levels.
 */
class OverlayMotionConstraintsTest {
    private val harness = PlannerHarness()

    @Test
    fun `no generated position falls inside the insets on any tested screen`() {
        PlannerHarness.TEST_SCREENS.forEachIndexed { index, screen ->
            val run = PlannerHarness(seed = index, initialGeometry = screen)
            val drops = Random(index)
            run.showLevel(1)
            repeat(10) {
                run.advanceBy(seconds = HOP_SEC).forEach(run::assertInsideSafeArea)
                run.dragTo(drops.nextInt(-500, 2_500), drops.nextInt(-500, 3_000))
                run.assertInsideSafeArea(run.motion)
            }
            run.showLevel(2)
            repeat(10) {
                run.advanceBy(seconds = 30).forEach(run::assertInsideSafeArea)
                run.dragTo(drops.nextInt(-500, 2_500), drops.nextInt(-500, 3_000))
                run.assertInsideSafeArea(run.motion)
            }
        }
    }

    @Test
    fun `with animations off level 1 never changes place`() {
        harness.showLevel(1)
        harness.planner.onAnimationsEnabledChanged(false)
        val start = harness.motion
        harness.advanceBy(seconds = 3 * HOP_SEC)
        assertEquals(start, harness.motion)
    }

    @Test
    fun `with animations off level 2 neither walks nor changes phase`() {
        harness.showLevel(2)
        harness.planner.onAnimationsEnabledChanged(false)
        val start = harness.motion
        harness.advanceBy(seconds = 5 * (EscalationConfig.LEVEL_2_WALK_SEC + EscalationConfig.LEVEL_2_STOP_SEC))
        assertEquals(start, harness.motion)
    }

    @Test
    fun `with animations off Likka is still placed when a level starts`() {
        harness.planner.onAnimationsEnabledChanged(false)
        harness.showLevel(1)
        assertNotNull(harness.planner.motion.value)
        harness.assertInsideSafeArea(harness.motion)
    }

    @Test
    fun `with animations off a level 1 drop still snaps to the nearest edge`() {
        harness.planner.onAnimationsEnabledChanged(false)
        harness.showLevel(1)
        harness.dragTo(x = 300, y = 1_300)
        assertEquals(OverlayEdge.BOTTOM, harness.motion.edge)
        assertEquals(harness.area.maxY, harness.motion.y)
    }

    @Test
    fun `with animations off a level 2 drop stays where it was dropped`() {
        harness.planner.onAnimationsEnabledChanged(false)
        harness.showLevel(2)
        harness.dragTo(x = 30, y = 300)
        val dropped = harness.motion
        harness.advanceBy(seconds = 60)
        assertEquals(dropped, harness.motion)
    }

    @Test
    fun `turning animations back on resumes the hop timer where it was`() {
        harness.showLevel(1)
        harness.advanceBy(seconds = 30)
        harness.planner.onAnimationsEnabledChanged(false)
        harness.advanceBy(seconds = 100)
        harness.planner.onAnimationsEnabledChanged(true)
        val start = harness.motion
        harness.advanceBy(seconds = HOP_SEC - 30 - 1, millis = 900)
        assertEquals(start, harness.motion)
        harness.advanceBy(millis = 100)
        assertNotEquals(start, harness.motion)
    }

    @Test
    fun `hidden level 1 keeps its place and resumes the hop countdown`() {
        harness.showLevel(1)
        harness.advanceBy(seconds = 30)
        val beforeHiding = harness.motion
        harness.hide()
        harness.advanceBy(seconds = 120)
        harness.show()
        assertEquals(beforeHiding, harness.motion)
        harness.advanceBy(seconds = HOP_SEC - 30 - 1, millis = 900)
        assertEquals(beforeHiding, harness.motion)
        harness.advanceBy(millis = 100)
        assertNotEquals(beforeHiding, harness.motion)
    }

    @Test
    fun `hidden level 2 resumes its walk where it was`() {
        harness.showLevel(2)
        harness.advanceBy(seconds = 2)
        val beforeHiding = harness.motion
        harness.hide()
        harness.advanceBy(seconds = 60)
        harness.show()
        assertEquals(beforeHiding, harness.motion)
        harness.advanceBy(seconds = EscalationConfig.LEVEL_2_WALK_SEC - 2 - 1, millis = 900)
        assertEquals(MotionMode.WALKING, harness.motion.mode)
        harness.advanceBy(millis = 100)
        assertEquals(MotionMode.STOPPED, harness.motion.mode)
    }

    @Test
    fun `the farewell freezes the movement`() {
        harness.showLevel(2)
        harness.advanceBy(seconds = 1)
        harness.startFarewell()
        val farewell = harness.motion
        harness.advanceBy(seconds = 10)
        assertEquals(farewell, harness.motion)
    }

    @Test
    fun `levels 0 and 3 have nothing to place`() {
        harness.showLevel(2)
        harness.showLevel(3)
        assertNull(harness.planner.motion.value)
        harness.showLevel(0)
        assertNull(harness.planner.motion.value)
    }

    @Test
    fun `a level before the first geometry is placed when the geometry arrives`() {
        val planner = OverlayMotionPlanner(FakeClock(), Random(1))
        planner.onOverlayStateChanged(LikkaOverlayState.NOTHING_TO_SHOW.copy(level = 1))
        assertNull(planner.motion.value)
        planner.onGeometryChanged(PlannerHarness.PHONE)
        assertEquals(MotionMode.ON_EDGE, planner.motion.value?.mode)
    }

    private companion object {
        const val HOP_SEC = EscalationConfig.LEVEL_1_HOP_INTERVAL_SEC
    }
}
