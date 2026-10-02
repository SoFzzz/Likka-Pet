package com.likkapet.presentation.components

import com.likkapet.presentation.theme.LikkaMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Compose extras of design system §1.7, drawn on the sprite-pixel grid and never baked into the
 * sheet: every extra of `likka_poses.json` is known, every pattern fits inside the 96×96 frame, the
 * foot shadow follows the feet, and the aura pulses once a second.
 */
class SpriteExtrasTest {
    private val assetsDir = File("src/main/assets/sprites")

    @Test
    fun `every extra listed in likka_poses json is one the code draws`() {
        val sheet = loadSheet()
        val listed = sheet.poses.values.flatMapTo(mutableSetOf()) { it.extras }

        assertEquals(SpriteExtra.entries.map { it.key }.toSet(), listed)
    }

    @Test
    fun `each pose gets its own extras`() {
        val sheet = loadSheet()

        assertEquals(setOf(SpriteExtra.SPARKLES), sheet.clipFor("happy")?.extras)
        assertEquals(setOf(SpriteExtra.SLEEP_Z), sheet.clipFor("sleeping")?.extras)
        assertEquals(setOf(SpriteExtra.SWEAT_DROP), sheet.clipFor("worried")?.extras)
        assertEquals(setOf(SpriteExtra.AURA), sheet.clipFor("fury")?.extras)
        assertEquals(emptySet<SpriteExtra>(), sheet.clipFor("idle")?.extras)
    }

    @Test
    fun `unknown extras are ignored`() {
        assertEquals(setOf(SpriteExtra.AURA), SpriteExtra.fromKeys(listOf("aura", "confetti")))
    }

    @Test
    fun `every pattern stays inside the frame at every step`() {
        val steps = (0 until 16).map { it * LikkaMotion.EXTRAS_STEP_MS }
        steps.forEach { ms ->
            SpriteExtras.sparkleSpots(ms).forEach { assertInsideFrame(SpriteExtras.SPARKLE, it) }
            SpriteExtras.sleepZSpots(ms).forEach { assertInsideFrame(SpriteExtras.SLEEP_Z, it) }
            assertInsideFrame(SpriteExtras.SWEAT_DROP, SpriteExtras.sweatDropSpot(ms))
        }
    }

    @Test
    fun `every point extra stays inside the stage circle, which clips it on the dashboard`() {
        val steps = (0 until 16).map { it * LikkaMotion.EXTRAS_STEP_MS }
        val placed =
            steps.flatMap { ms ->
                SpriteExtras.sparkleSpots(ms).map { SpriteExtras.SPARKLE to it } +
                    SpriteExtras.sleepZSpots(ms).map { SpriteExtras.SLEEP_Z to it } +
                    (SpriteExtras.SWEAT_DROP to SpriteExtras.sweatDropSpot(ms))
            }
        val center = SpriteMetrics.FRAME_PX / 2.0
        placed.forEach { (pattern, at) ->
            pattern.pixels.forEach { pixel ->
                val dx = at.x + pixel.x + 0.5 - center
                val dy = at.y + pixel.y + 0.5 - center
                assertTrue(
                    "$at pixel $pixel is cut by the stage",
                    dx * dx + dy * dy <= SpriteExtras.STAGE_SAFE_RADIUS_PX.toDouble().let { it * it },
                )
            }
        }
    }

    @Test
    fun `the sparkles take turns and the z's rise one by one`() {
        val twoSteps = 2 * LikkaMotion.EXTRAS_STEP_MS

        assertTrue(SpriteExtras.sparkleSpots(0) != SpriteExtras.sparkleSpots(twoSteps))
        assertEquals(1, SpriteExtras.sleepZSpots(0).size)
        assertEquals(2, SpriteExtras.sleepZSpots(twoSteps).size)
        assertEquals(3, SpriteExtras.sleepZSpots(2 * twoSteps).size)
        assertEquals(0, SpriteExtras.sleepZSpots(3 * twoSteps).size)
    }

    @Test
    fun `the aura grows and shrinks one sprite pixel at a time, once a second`() {
        val radii = (0 until SpriteExtras.AURA_RADII.size).map { SpriteExtras.auraRadius(it * LikkaMotion.EXTRAS_STEP_MS) }

        assertEquals(SpriteExtras.AURA_RADII, radii)
        assertEquals(SpriteExtras.auraRadius(0), SpriteExtras.auraRadius(LikkaMotion.AURA_PULSE_MS))
        (radii + radii.first()).zipWithNext().forEach { (a, b) -> assertTrue("$a -> $b", kotlin.math.abs(a - b) <= 1) }
    }

    @Test
    fun `the halo ring keeps its 2 sprite pixel shape`() {
        val offsets = SpriteExtras.dilationOffsets(SpriteMetrics.HALO_PX)

        assertTrue(offsets.all { (dx, dy) -> dx * dx + dy * dy <= 5 && (dx != 0 || dy != 0) })
        assertEquals(20, offsets.size)
    }

    @Test
    fun `the foot shadow is wider than the feet and cut at the frame's sides`() {
        val rows = SpriteExtras.footShadowRows(40..56)

        assertEquals(SpriteExtras.FOOT_SHADOW_MARGINS.size, rows.size)
        assertEquals(SpriteMetrics.FEET_LINE_Y, rows.first().first)
        assertTrue(rows.all { (_, xs) -> xs.first < 40 && xs.last > 56 })
        assertTrue(rows.last().first < SpriteMetrics.FRAME_PX)
        assertEquals(SpriteMetrics.FRAME_PX - 1, SpriteExtras.footShadowRows(77..95).maxOf { it.second.last })
    }

    @Test
    fun `no feet means no shadow`() {
        assertNull(SpriteExtras.feetSpan { _, _ -> false })
    }

    @Test
    fun `every frame of the sheet has feet on the feet line`() {
        val sheet = loadSheet()
        val image = ImageIO.read(File(assetsDir, "likka.png"))
        sheet.frames.forEach { frame ->
            val span = SpriteExtras.feetSpan { x, y -> image.getRGB(frame.x + x, frame.y + y) ushr ALPHA_SHIFT != 0 }
            assertNotNull("frame at ${frame.x},${frame.y} has no feet", span)
        }
    }

    private fun assertInsideFrame(
        pattern: PixelPattern,
        at: SpritePoint,
    ) {
        val isInside =
            at.x >= 0 && at.y >= 0 && at.x + pattern.width <= SpriteMetrics.FRAME_PX && at.y + pattern.height <= SpriteMetrics.FRAME_PX
        assertTrue("$at + ${pattern.width}×${pattern.height} leaves the frame", isInside)
    }

    private fun loadSheet(): LikkaSpriteSheet {
        assumeTrue("Skipped: ${assetsDir.absolutePath} does not exist", assetsDir.isDirectory)
        return LikkaSpriteSheet.parse(File(assetsDir, "likka.json").readText(), File(assetsDir, "likka_poses.json").readText())
    }

    private companion object {
        const val ALPHA_SHIFT = 24
    }
}
