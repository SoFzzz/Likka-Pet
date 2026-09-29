package com.likkapet.domain

import com.likkapet.domain.model.OverlayEdge
import kotlin.math.abs
import kotlin.random.Random

internal data class EdgePosition(
    val edge: OverlayEdge,
    val point: ScreenPoint,
)

/**
 * Level 1 positions (RF-O09): the window flush against the left or right edge, or resting on the
 * bottom one right above the navigation bar, always inside the [SafeArea] and on the sprite-pixel
 * grid along the edge. The window itself never leaves the screen: the "half peeking" look depends
 * on the `peek` art being drawn cut off by one side of its frame.
 */
internal object EdgePlacement {
    fun at(
        area: SafeArea,
        edge: OverlayEdge,
        near: ScreenPoint,
    ): EdgePosition =
        when (edge) {
            OverlayEdge.LEFT -> EdgePosition(edge, ScreenPoint(area.minX, area.snapY(near.y)))
            OverlayEdge.RIGHT -> EdgePosition(edge, ScreenPoint(area.maxX, area.snapY(near.y)))
            OverlayEdge.BOTTOM -> EdgePosition(edge, ScreenPoint(area.snapX(near.x), area.maxY))
        }

    /** Where a Level 1 drag ends: the allowed edge closest to the drop point (ties: left, right, bottom). */
    fun nearest(
        area: SafeArea,
        drop: ScreenPoint,
    ): EdgePosition {
        val point = area.clamp(drop)
        val distances =
            mapOf(
                OverlayEdge.LEFT to point.x - area.minX,
                OverlayEdge.RIGHT to area.maxX - point.x,
                OverlayEdge.BOTTOM to area.maxY - point.y,
            )
        return at(area, OverlayEdge.entries.minBy { distances.getValue(it) }, point)
    }

    /**
     * A random allowed position, visibly different from [avoid] (see [isDistinct]). If none exists
     * (a tiny screen), stays at [avoid].
     */
    fun random(
        area: SafeArea,
        random: Random,
        avoid: EdgePosition?,
    ): EdgePosition {
        val candidatesByEdge =
            OverlayEdge.entries
                .associateWith { edge -> allOn(area, edge).filter { avoid == null || isDistinct(area, it, avoid) } }
                .filterValues { it.isNotEmpty() }
        if (candidatesByEdge.isEmpty()) return checkNotNull(avoid) { "Every edge has at least one position" }
        val edge = candidatesByEdge.keys.random(random)
        return candidatesByEdge.getValue(edge).random(random)
    }

    /** At least one window away on either axis, so even a hop to another edge near a corner is a visible move. */
    fun isDistinct(
        area: SafeArea,
        candidate: EdgePosition,
        current: EdgePosition,
    ): Boolean {
        val geometry = area.geometry
        return abs(candidate.point.x - current.point.x) >= geometry.windowWidthPx ||
            abs(candidate.point.y - current.point.y) >= geometry.windowHeightPx
    }

    private fun allOn(
        area: SafeArea,
        edge: OverlayEdge,
    ): List<EdgePosition> =
        when (edge) {
            OverlayEdge.LEFT -> area.gridYs().map { EdgePosition(edge, ScreenPoint(area.minX, it)) }
            OverlayEdge.RIGHT -> area.gridYs().map { EdgePosition(edge, ScreenPoint(area.maxX, it)) }
            OverlayEdge.BOTTOM -> area.gridXs().map { EdgePosition(edge, ScreenPoint(it, area.maxY)) }
        }
}
