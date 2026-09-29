package com.likkapet.domain

import com.likkapet.domain.model.MotionPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Walk pose by 45° sectors of θ = atan2(dy, dx), screen y down (owner decision for task 3; replaces
 * the 60° table of documentación §9.6), and the sprite-pixel steps of Level 2.
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
    fun `lower diagonals have their own poses`() {
        assertEquals(MotionPose.WALK_DIAG_DOWN_RIGHT, WalkPlanning.walkPoseFor(10, 10))
        assertEquals(MotionPose.WALK_DIAG_DOWN_LEFT, WalkPlanning.walkPoseFor(-10, 10))
    }

    @Test
    fun `upper diagonals at 225 and 315 degrees walk up`() {
        assertEquals(MotionPose.WALK_UP, WalkPlanning.walkPoseFor(-10, -10))
        assertEquals(MotionPose.WALK_UP, WalkPlanning.walkPoseFor(10, -10))
        assertEquals(MotionPose.WALK_UP, WalkPlanning.walkPoseForAngle(225.0))
        assertEquals(MotionPose.WALK_UP, WalkPlanning.walkPoseForAngle(315.0))
    }

    @Test
    fun `no movement has no pose`() {
        assertNull(WalkPlanning.walkPoseFor(0, 0))
    }

    @Test
    fun `each sector includes its exact lower bound`() {
        assertEquals(MotionPose.WALK_RIGHT, WalkPlanning.walkPoseForAngle(-22.5))
        assertEquals(MotionPose.WALK_DIAG_DOWN_RIGHT, WalkPlanning.walkPoseForAngle(22.5))
        assertEquals(MotionPose.WALK_DOWN, WalkPlanning.walkPoseForAngle(67.5))
        assertEquals(MotionPose.WALK_DIAG_DOWN_LEFT, WalkPlanning.walkPoseForAngle(112.5))
        assertEquals(MotionPose.WALK_LEFT, WalkPlanning.walkPoseForAngle(157.5))
        assertEquals(MotionPose.WALK_UP, WalkPlanning.walkPoseForAngle(202.5))
        assertEquals(MotionPose.WALK_RIGHT, WalkPlanning.walkPoseForAngle(337.5))
    }

    @Test
    fun `just below each lower bound belongs to the previous sector`() {
        assertEquals(MotionPose.WALK_UP, WalkPlanning.walkPoseForAngle(-22.5 - EPSILON))
        assertEquals(MotionPose.WALK_RIGHT, WalkPlanning.walkPoseForAngle(22.5 - EPSILON))
        assertEquals(MotionPose.WALK_DIAG_DOWN_RIGHT, WalkPlanning.walkPoseForAngle(67.5 - EPSILON))
        assertEquals(MotionPose.WALK_DOWN, WalkPlanning.walkPoseForAngle(112.5 - EPSILON))
        assertEquals(MotionPose.WALK_DIAG_DOWN_LEFT, WalkPlanning.walkPoseForAngle(157.5 - EPSILON))
        assertEquals(MotionPose.WALK_LEFT, WalkPlanning.walkPoseForAngle(202.5 - EPSILON))
        assertEquals(MotionPose.WALK_UP, WalkPlanning.walkPoseForAngle(337.5 - EPSILON))
    }

    @Test
    fun `both ends of the atan2 range walk left`() {
        assertEquals(MotionPose.WALK_LEFT, WalkPlanning.walkPoseForAngle(180.0))
        assertEquals(MotionPose.WALK_LEFT, WalkPlanning.walkPoseForAngle(-180.0))
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
