package com.likkapet.domain

import com.likkapet.domain.model.OverlayGeometry

/** A window's top-left corner in physical pixels. */
internal data class ScreenPoint(
    val x: Int,
    val y: Int,
)

/**
 * Range of top-left corners that keep the whole window out of the system insets (RF-O13). If the
 * window does not fit, the range collapses to the safe area's top/left edge instead of failing.
 */
internal class SafeArea(
    val geometry: OverlayGeometry,
) {
    val minX = geometry.insets.left
    val maxX = maxOf(minX, geometry.screenWidthPx - geometry.insets.right - geometry.windowWidthPx)
    val minY = geometry.insets.top
    val maxY = maxOf(minY, geometry.screenHeightPx - geometry.insets.bottom - geometry.windowHeightPx)

    private val gridPx = geometry.spritePixelPx

    fun clamp(point: ScreenPoint) = ScreenPoint(point.x.coerceIn(minX, maxX), point.y.coerceIn(minY, maxY))

    /** Clamps, then moves down to the sprite-pixel grid that starts at the safe area's corner. */
    fun snapX(x: Int): Int = snap(x.coerceIn(minX, maxX), minX)

    fun snapY(y: Int): Int = snap(y.coerceIn(minY, maxY), minY)

    fun gridXs(): IntProgression = minX..maxX step gridPx

    fun gridYs(): IntProgression = minY..maxY step gridPx

    private fun snap(
        value: Int,
        origin: Int,
    ): Int = origin + (value - origin) / gridPx * gridPx
}
