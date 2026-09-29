package com.likkapet.domain

import com.likkapet.domain.model.MotionPose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sign
import kotlin.random.Random

/** Level 2 walking (RF-O10/RF-O11): targets in the central zone, sprite-pixel steps and the walk pose. */
internal object WalkPlanning {
    private const val DEGREES_PER_RADIAN = 180.0 / PI
    private const val FULL_TURN_DEGREES = 360.0
    private const val SECTOR_DEGREES = 45.0
    private const val HALF_SECTOR_DEGREES = SECTOR_DEGREES / 2

    // Eight 45° sectors centered on the axes and diagonals, starting at -22.5° (screen y grows
    // downwards, so 90° is down). The sheet has no up-diagonals, so the three upper sectors all
    // use WALK_UP. This replaces the six 60° sectors of documentación §9.6 (pending doc fix, with
    // change-log entry 59): with those, an exact 225° up-left diagonal walked as WALK_LEFT.
    private val WALK_POSE_BY_SECTOR =
        listOf(
            MotionPose.WALK_RIGHT, // [-22.5, 22.5)
            MotionPose.WALK_DIAG_DOWN_RIGHT, // [22.5, 67.5)
            MotionPose.WALK_DOWN, // [67.5, 112.5)
            MotionPose.WALK_DIAG_DOWN_LEFT, // [112.5, 157.5)
            MotionPose.WALK_LEFT, // [157.5, 202.5)
            MotionPose.WALK_UP, // [202.5, 247.5)
            MotionPose.WALK_UP, // [247.5, 292.5)
            MotionPose.WALK_UP, // [292.5, 337.5)
        )

    /**
     * A random stop point whose window center lies in the central
     * [EscalationConfig.LEVEL_2_CENTER_ZONE_FRACTION] of the safe area on each axis; the window is
     * then clamped (and snapped to the grid) so all of it stays inside the safe area.
     */
    fun centralZoneTarget(
        area: SafeArea,
        random: Random,
    ): ScreenPoint {
        val geometry = area.geometry
        val insets = geometry.insets
        val centerX = randomInCentralZone(insets.left, geometry.screenWidthPx - insets.right, random)
        val centerY = randomInCentralZone(insets.top, geometry.screenHeightPx - insets.bottom, random)
        return ScreenPoint(
            area.snapX(centerX - geometry.windowWidthPx / 2),
            area.snapY(centerY - geometry.windowHeightPx / 2),
        )
    }

    /**
     * One step along the straight line to [target]: exactly [stepPx] on the dominant axis (less on
     * the last step) and the proportional share on the other one, so the walk never overshoots.
     */
    fun stepTowards(
        from: ScreenPoint,
        target: ScreenPoint,
        stepPx: Int,
    ): ScreenPoint {
        val dx = target.x - from.x
        val dy = target.y - from.y
        if (dx == 0 && dy == 0) return from
        return if (abs(dx) >= abs(dy)) {
            val moveX = dx.sign * minOf(stepPx, abs(dx))
            ScreenPoint(from.x + moveX, from.y + dy * moveX / dx)
        } else {
            val moveY = dy.sign * minOf(stepPx, abs(dy))
            ScreenPoint(from.x + dx * moveY / dy, from.y + moveY)
        }
    }

    /** Walk pose for a movement vector in screen coordinates, or null when there is no movement. */
    fun walkPoseFor(
        dx: Int,
        dy: Int,
    ): MotionPose? {
        if (dx == 0 && dy == 0) return null
        return walkPoseForAngle(atan2(dy.toDouble(), dx.toDouble()) * DEGREES_PER_RADIAN)
    }

    /** θ = atan2(dy, dx) in degrees, any turn; each sector includes its lower bound. */
    fun walkPoseForAngle(degrees: Double): MotionPose {
        val fromFirstSectorStart = (degrees + HALF_SECTOR_DEGREES).mod(FULL_TURN_DEGREES)
        return WALK_POSE_BY_SECTOR[floor(fromFirstSectorStart / SECTOR_DEGREES).toInt()]
    }

    private fun randomInCentralZone(
        safeStart: Int,
        safeEnd: Int,
        random: Random,
    ): Int {
        val center = (safeStart + safeEnd) / 2
        val halfZone = ((safeEnd - safeStart) * EscalationConfig.LEVEL_2_CENTER_ZONE_FRACTION / 2).toInt().coerceAtLeast(0)
        return random.nextInt(center - halfZone, center + halfZone + 1)
    }
}
