package com.likkapet.domain.model

/**
 * What `OverlayMotionPlanner` needs to know about the screen, in physical pixels. The service sends
 * a new one whenever any value changes (rotation, insets, or the window resized by a level change
 * or a longer roast).
 */
data class OverlayGeometry(
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    // System bars and cutouts: the forbidden zone of RF-O13
    val insets: OverlayInsets,
    val windowWidthPx: Int,
    val windowHeightPx: Int,
    // Size of one sprite pixel at the current level's scale; the movement grid
    val spritePixelPx: Int,
) {
    init {
        require(screenWidthPx > 0 && screenHeightPx > 0) { "Empty screen: $screenWidthPx×$screenHeightPx" }
        require(windowWidthPx > 0 && windowHeightPx > 0) { "Empty window: $windowWidthPx×$windowHeightPx" }
        require(spritePixelPx > 0) { "Sprite pixel must be positive: $spritePixelPx" }
    }
}

data class OverlayInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    companion object {
        val NONE = OverlayInsets(left = 0, top = 0, right = 0, bottom = 0)
    }
}
