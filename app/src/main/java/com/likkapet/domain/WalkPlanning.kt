package com.likkapet.domain

import com.likkapet.domain.model.MotionPose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sign
import kotlin.random.Random

/**
 * Level 2 walking (RF-O10/RF-O11): cardinal 4-direction tramos, straight-line steps, and return targets.
 */
internal object WalkPlanning {
    private const val DEGREES_PER_RADIAN = 180.0 / PI
    private const val FULL_TURN_DEGREES = 360.0
    private const val SECTOR_DEGREES = 90.0
    private const val HALF_SECTOR_DEGREES = SECTOR_DEGREES / 2

    // Four 90° sectors centered on the cardinal axes, starting at -45° (screen y down).
    // No diagonals are used (owner decision for Level 2 redesign).
    private val CARDINAL_POSES =
        listOf(
            MotionPose.WALK_RIGHT, // [-45, 45)
            MotionPose.WALK_DOWN, // [45, 135)
            MotionPose.WALK_LEFT, // [135, 225)
            MotionPose.WALK_UP, // [225, 315)
        )

    /**
     * How much space (in physical px) is available in [direction] from [from] before hitting the safe area boundary.
     */
    fun availableSpace(
        area: SafeArea,
        from: ScreenPoint,
        direction: WalkDirection,
    ): Int =
        when (direction) {
            WalkDirection.UP -> (from.y - area.minY).coerceAtLeast(0)
            WalkDirection.DOWN -> (area.maxY - from.y).coerceAtLeast(0)
            WalkDirection.LEFT -> (from.x - area.minX).coerceAtLeast(0)
            WalkDirection.RIGHT -> (area.maxX - from.x).coerceAtLeast(0)
        }

    /**
     * Plans the next straight walking segment in one of the 4 cardinal directions.
     * Picks a direction distinct from [previousDirection] with space for at least [minSteps];
     * if none fits, picks the direction with the most space. Returns null if not even one step fits.
     */
    fun planNextTramo(
        area: SafeArea,
        from: ScreenPoint,
        stepPx: Int,
        minSteps: Int,
        previousDirection: WalkDirection?,
        random: Random,
    ): WalkTramo? {
        val candidates = WalkDirection.entries.filter { it != previousDirection }
        val fitting = candidates.filter { availableSpace(area, from, it) / stepPx >= minSteps }
        val chosenDirection =
            if (fitting.isNotEmpty()) {
                fitting.random(random)
            } else {
                val maxSpace = candidates.maxOfOrNull { availableSpace(area, from, it) } ?: 0
                if (maxSpace / stepPx < 1) return null
                candidates.filter { availableSpace(area, from, it) == maxSpace }.random(random)
            }

        val maxSteps = availableSpace(area, from, chosenDirection) / stepPx
        if (maxSteps < 1) return null

        val steps =
            if (maxSteps >= minSteps) {
                if (minSteps == maxSteps) minSteps else random.nextInt(minSteps, maxSteps + 1)
            } else {
                maxSteps
            }

        val target =
            ScreenPoint(
                x = from.x + chosenDirection.dx * steps * stepPx,
                y = from.y + chosenDirection.dy * steps * stepPx,
            )
        return WalkTramo(chosenDirection, target, steps)
    }

    /**
     * A random stop point whose window center lies in the central
     * [EscalationConfig.LEVEL_2_CENTER_ZONE_FRACTION] of the safe area on each axis; used for
     * the return walk after a drag.
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

    /** Walk pose for a movement vector in screen coordinates (dominant axis), or null when there is no movement. */
    fun walkPoseFor(
        dx: Int,
        dy: Int,
    ): MotionPose? {
        if (dx == 0 && dy == 0) return null
        return if (abs(dx) >= abs(dy)) {
            if (dx > 0) MotionPose.WALK_RIGHT else MotionPose.WALK_LEFT
        } else {
            if (dy > 0) MotionPose.WALK_DOWN else MotionPose.WALK_UP
        }
    }

    /** θ = atan2(dy, dx) in degrees, 4 cardinal sectors of 90°; each sector includes its lower bound. */
    fun walkPoseForAngle(degrees: Double): MotionPose {
        val fromFirstSectorStart = (degrees + HALF_SECTOR_DEGREES).mod(FULL_TURN_DEGREES)
        return CARDINAL_POSES[floor(fromFirstSectorStart / SECTOR_DEGREES).toInt()]
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

/** The 4 cardinal walk directions for Level 2 movement. */
internal enum class WalkDirection(
    val pose: MotionPose,
    val dx: Int,
    val dy: Int,
) {
    UP(MotionPose.WALK_UP, 0, -1),
    DOWN(MotionPose.WALK_DOWN, 0, 1),
    LEFT(MotionPose.WALK_LEFT, -1, 0),
    RIGHT(MotionPose.WALK_RIGHT, 1, 0),
}

/** A planned straight walking segment with a chosen cardinal direction, target, and step count. */
internal data class WalkTramo(
    val direction: WalkDirection,
    val target: ScreenPoint,
    val steps: Int,
)
