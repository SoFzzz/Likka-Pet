package com.likkapet.domain

import com.likkapet.domain.model.MotionPose
import com.likkapet.domain.model.OverlayGeometry
import com.likkapet.domain.model.OverlayInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 4 cardinal directions without diagonals (owner decision for Level 2 redesign), 90° sectors,
 * and straight-line tramo planning.
 */
class WalkDirectionTest {
    @Test
    fun `straight vectors along the four axes`() {
        assertEquals(MotionPose.WALK_RIGHT, WalkPlanning.walkPoseFor(10, 0))
        assertEquals(MotionPose.WALK_DOWN, WalkPlanning.walkPoseFor(0, 10))
        assertEquals(MotionPose.WALK_LEFT, WalkPlanning.walkPoseFor(-10, 0))
        assertEquals(MotionPose.WALK_UP, WalkPlanning.walkPoseFor(0, -10))
    }

    @Test
    fun `diagonal vectors map to the dominant cardinal axis without diagonals`() {
        assertEquals(MotionPose.WALK_RIGHT, WalkPlanning.walkPoseFor(15, 10))
        assertEquals(MotionPose.WALK_DOWN, WalkPlanning.walkPoseFor(10, 15))
        assertEquals(MotionPose.WALK_LEFT, WalkPlanning.walkPoseFor(-15, 10))
        assertEquals(MotionPose.WALK_UP, WalkPlanning.walkPoseFor(-10, -15))
    }

    @Test
    fun `no movement has no pose`() {
        assertNull(WalkPlanning.walkPoseFor(0, 0))
    }

    @Test
    fun `each 90-degree sector includes its exact lower bound`() {
        assertEquals(MotionPose.WALK_RIGHT, WalkPlanning.walkPoseForAngle(-45.0))
        assertEquals(MotionPose.WALK_DOWN, WalkPlanning.walkPoseForAngle(45.0))
        assertEquals(MotionPose.WALK_LEFT, WalkPlanning.walkPoseForAngle(135.0))
        assertEquals(MotionPose.WALK_UP, WalkPlanning.walkPoseForAngle(225.0))
        assertEquals(MotionPose.WALK_RIGHT, WalkPlanning.walkPoseForAngle(315.0))
    }

    @Test
    fun `just below each lower bound belongs to the previous sector`() {
        assertEquals(MotionPose.WALK_UP, WalkPlanning.walkPoseForAngle(-45.0 - EPSILON))
        assertEquals(MotionPose.WALK_RIGHT, WalkPlanning.walkPoseForAngle(45.0 - EPSILON))
        assertEquals(MotionPose.WALK_DOWN, WalkPlanning.walkPoseForAngle(135.0 - EPSILON))
        assertEquals(MotionPose.WALK_LEFT, WalkPlanning.walkPoseForAngle(225.0 - EPSILON))
    }

    @Test
    fun `both ends of the atan2 range walk left`() {
        assertEquals(MotionPose.WALK_LEFT, WalkPlanning.walkPoseForAngle(180.0))
        assertEquals(MotionPose.WALK_LEFT, WalkPlanning.walkPoseForAngle(-180.0))
    }

    @Test
    fun `available space correctly measures distance to safe area bounds`() {
        val geometry = OverlayGeometry(720, 1600, OverlayInsets(0, 72, 0, 96), 198, 240, 3)
        val area = SafeArea(geometry)
        val point = ScreenPoint(100, 300)

        assertEquals(point.y - area.minY, WalkPlanning.availableSpace(area, point, WalkDirection.UP))
        assertEquals(area.maxY - point.y, WalkPlanning.availableSpace(area, point, WalkDirection.DOWN))
        assertEquals(point.x - area.minX, WalkPlanning.availableSpace(area, point, WalkDirection.LEFT))
        assertEquals(area.maxX - point.x, WalkPlanning.availableSpace(area, point, WalkDirection.RIGHT))
    }

    @Test
    fun `planNextTramo picks a different direction with enough space`() {
        val geometry = OverlayGeometry(720, 1600, OverlayInsets(0, 72, 0, 96), 198, 240, 3)
        val area = SafeArea(geometry)
        val center = ScreenPoint((area.minX + area.maxX) / 2, (area.minY + area.maxY) / 2)
        val stepPx = 3
        val minSteps = 30

        val tramo =
            WalkPlanning.planNextTramo(
                area = area,
                from = center,
                stepPx = stepPx,
                minSteps = minSteps,
                previousDirection = WalkDirection.RIGHT,
                random = Random(42),
            )
        assertNotNull(tramo)
        val plan = checkNotNull(tramo)
        assertNotEquals(WalkDirection.RIGHT, plan.direction)
        assertTrue(plan.steps >= minSteps)
    }

    @Test
    fun `planNextTramo returns null when no step can fit in any direction`() {
        val geometry = OverlayGeometry(200, 200, OverlayInsets(0, 0, 0, 0), 200, 200, 2)
        val area = SafeArea(geometry)
        val point = ScreenPoint(0, 0)

        val tramo =
            WalkPlanning.planNextTramo(
                area = area,
                from = point,
                stepPx = 2,
                minSteps = 30,
                previousDirection = null,
                random = Random(42),
            )
        assertNull(tramo)
    }

    @Test
    fun `a step moves one step on the dominant axis and the proportional share on the other`() {
        val next = WalkPlanning.stepTowards(ScreenPoint(0, 0), ScreenPoint(30, 15), stepPx = 3)
        assertEquals(ScreenPoint(3, 1), next)
    }

    @Test
    fun `the last step stops exactly on the target`() {
        val target = ScreenPoint(2, -1)
        assertEquals(target, WalkPlanning.stepTowards(ScreenPoint(0, 0), target, stepPx = 3))
        assertEquals(target, WalkPlanning.stepTowards(target, target, stepPx = 3))
    }

    @Test
    fun `repeated steps reach any target without overshooting`() {
        val target = ScreenPoint(-47, 131)
        var point = ScreenPoint(0, 0)
        repeat(100) { point = WalkPlanning.stepTowards(point, target, stepPx = 3) }
        assertEquals(target, point)
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
