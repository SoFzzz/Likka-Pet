package com.likkapet.presentation.components

import com.likkapet.presentation.theme.LikkaMotion

/** The Compose extras a pose may list in `likka_poses.json` (design system §1.7); never baked into the sheet. */
enum class SpriteExtra(
    val key: String,
) {
    SPARKLES("sparkles"),
    SLEEP_Z("z"),
    SWEAT_DROP("sweat_drop"),
    AURA("aura"),
    ;

    companion object {
        /** The extras among [keys] this code knows how to draw; an unknown key is ignored. */
        fun fromKeys(keys: List<String>): Set<SpriteExtra> = keys.mapNotNull { key -> entries.firstOrNull { it.key == key } }.toSet()
    }
}

/** A point inside the 96×96 frame, in sprite pixels. */
data class SpritePoint(
    val x: Int,
    val y: Int,
)

/** One sprite pixel of a [PixelPattern] and its role: [PixelPattern.OUTLINE], [PixelPattern.FILL] or [PixelPattern.SHINE]. */
data class PatternPixel(
    val x: Int,
    val y: Int,
    val role: Char,
)

/** A tiny drawing in sprite pixels, written as rows of characters; '.' is transparent. */
class PixelPattern(
    rows: List<String>,
) {
    val width = rows.maxOf { it.length }
    val height = rows.size
    val pixels: List<PatternPixel> =
        rows.flatMapIndexed { y, row ->
            row.mapIndexedNotNull { x, role -> PatternPixel(x, y, role).takeIf { role != TRANSPARENT } }
        }

    companion object {
        const val TRANSPARENT = '.'
        const val OUTLINE = 'o'
        const val FILL = 'f'
        const val SHINE = 'h'
    }
}

/**
 * Where and how the extras are drawn, in sprite pixels of the 96×96 frame, so they follow the pixel
 * grid at any integer scale (design system §1.7). Placed by hand around the art of the tag each one
 * goes with: `sparkles` around `idle`, `z` above the head of `sleep`, `sweat_drop` beside the hood
 * of `look_around`, and the aura and the foot shadow around the frame's own silhouette. The point
 * extras stay inside [STAGE_SAFE_RADIUS_PX] of the frame's center, so the round stage of the
 * dashboard, which clips its content, never cuts them.
 */
object SpriteExtras {
    // A four-point star with the sprite's own 1 px outline.
    val SPARKLE =
        PixelPattern(
            listOf(
                "...o...",
                "..ofo..",
                ".oofoo.",
                "offfffo",
                ".oofoo.",
                "..ofo..",
                "...o...",
            ),
        )

    // Two sets that take turns, so the sparkles twinkle.
    val SPARKLE_SPOTS =
        listOf(
            listOf(SpritePoint(12, 22), SpritePoint(80, 58)),
            listOf(SpritePoint(78, 22), SpritePoint(8, 56)),
        )

    val SLEEP_Z =
        PixelPattern(
            listOf(
                "fffff",
                "...f.",
                "..f..",
                ".f...",
                "fffff",
            ),
        )

    // Rising from the head: one more z each step, then none.
    val SLEEP_Z_SPOTS = listOf(SpritePoint(72, 40), SpritePoint(76, 30), SpritePoint(79, 20))

    val SWEAT_DROP =
        PixelPattern(
            listOf(
                "...o...",
                "..ofo..",
                ".offfo.",
                "offhffo",
                "offfffo",
                ".offfo.",
                "..ooo..",
            ),
        )
    val SWEAT_DROP_SPOT = SpritePoint(67, 24)

    // The drop slides down this many sprite pixels, one per step, then starts over.
    const val SWEAT_DROP_FALL_PX = 2

    // The aura's thickness through one pulse: grows and shrinks one sprite pixel at a time.
    val AURA_RADII = listOf(2, 3, 4, 3)

    // The foot shadow: one row per entry from the feet line down, each wider than the feet by that
    // many sprite pixels on both sides (a flat pixel ellipse).
    val FOOT_SHADOW_MARGINS = listOf(2, 4, 4, 2)

    // Distance from the frame's center within which a point extra is never cut by the stage circle
    // (its radius is half the frame, minus room for the 3dp border).
    const val STAGE_SAFE_RADIUS_PX = 46

    // Rows above the feet line (inclusive) where the feet are looked for.
    const val FEET_SCAN_ROWS = 6

    /** Twinkle set shown at [elapsedMs] (each set stays two extra steps). */
    fun sparkleSpots(elapsedMs: Long): List<SpritePoint> = SPARKLE_SPOTS[(steps(elapsedMs) / 2 % SPARKLE_SPOTS.size).toInt()]

    /** The z's on screen at [elapsedMs]: 1, 2, 3, then none, two extra steps each. */
    fun sleepZSpots(elapsedMs: Long): List<SpritePoint> {
        val phase = steps(elapsedMs) / 2 % (SLEEP_Z_SPOTS.size + 1)
        return SLEEP_Z_SPOTS.take(((phase + 1) % (SLEEP_Z_SPOTS.size + 1)).toInt())
    }

    fun sweatDropSpot(elapsedMs: Long): SpritePoint =
        SWEAT_DROP_SPOT.copy(y = SWEAT_DROP_SPOT.y + (steps(elapsedMs) % (SWEAT_DROP_FALL_PX + 1)).toInt())

    /** Aura thickness at [elapsedMs]: one full grow-and-shrink every [LikkaMotion.AURA_PULSE_MS]. */
    fun auraRadius(elapsedMs: Long): Int {
        val intoPulseMs = elapsedMs % LikkaMotion.AURA_PULSE_MS
        return AURA_RADII[(intoPulseMs * AURA_RADII.size / LikkaMotion.AURA_PULSE_MS).toInt()]
    }

    /** The x span of the feet: opaque pixels in the [FEET_SCAN_ROWS] rows ending on the feet line; null if none. */
    fun feetSpan(isOpaque: (x: Int, y: Int) -> Boolean): IntRange? {
        val rows = (SpriteMetrics.FEET_LINE_Y - FEET_SCAN_ROWS + 1)..SpriteMetrics.FEET_LINE_Y
        val xs = (0 until SpriteMetrics.FRAME_PX).filter { x -> rows.any { y -> isOpaque(x, y) } }
        return if (xs.isEmpty()) null else xs.first()..xs.last()
    }

    /** The shadow's rows under [feet]: (y, x span), cut at the frame's sides like the art itself (`peek`). */
    fun footShadowRows(feet: IntRange): List<Pair<Int, IntRange>> =
        FOOT_SHADOW_MARGINS.mapIndexed { row, margin ->
            val y = SpriteMetrics.FEET_LINE_Y + row
            y to (feet.first - margin).coerceAtLeast(0)..(feet.last + margin).coerceAtMost(SpriteMetrics.FRAME_PX - 1)
        }

    /**
     * Every sprite-pixel shift within [radius]: the silhouette drawn at each of them makes a rounded
     * ring that follows the pixel grid (the halo, design system §1.7, and the aura).
     */
    fun dilationOffsets(radius: Int): List<Pair<Int, Int>> =
        (-radius..radius)
            .flatMap { dx -> (-radius..radius).map { dy -> dx to dy } }
            .filter { (dx, dy) -> (dx != 0 || dy != 0) && dx * dx + dy * dy <= radius * radius + 1 }

    private fun steps(elapsedMs: Long): Long = elapsedMs / LikkaMotion.EXTRAS_STEP_MS
}
