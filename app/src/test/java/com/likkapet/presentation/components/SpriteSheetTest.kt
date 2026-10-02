package com.likkapet.presentation.components

import com.likkapet.domain.model.MotionPose
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Sprite files of design system §1.7 / documentación §12.1: the 10 sheet tags exist, every pose
 * points to an existing tag, every frame is 96×96. `assets/sprites/` is local (not versioned), so
 * the test is skipped with an explicit message when the folder is missing, never passed silently.
 */
class SpriteSheetTest {
    private val assetsDir = File("src/main/assets/sprites")

    private fun loadSheet(): LikkaSpriteSheet {
        assumeTrue(
            "Skipped: ${assetsDir.absolutePath} does not exist; copy sprites/likka.* there to validate the sheet",
            assetsDir.isDirectory,
        )
        return LikkaSpriteSheet.parse(
            File(assetsDir, "likka.json").readText(),
            File(assetsDir, "likka_poses.json").readText(),
        )
    }

    @Test
    fun `likka json has exactly the 10 sheet tags`() {
        assertEquals(SHEET_TAGS, loadSheet().tags.keys)
    }

    @Test
    fun `every pose points to an existing tag`() {
        val sheet = loadSheet()
        sheet.poses.forEach { (pose, spec) ->
            assertTrue("pose $pose -> missing tag ${spec.tag}", spec.tag in sheet.tags)
        }
    }

    @Test
    fun `every frame measures 96x96 and lies inside likka png`() {
        val sheet = loadSheet()
        val png = ImageIO.read(File(assetsDir, "likka.png"))
        sheet.frames.forEachIndexed { i, frame ->
            assertEquals("frame $i width", SpriteMetrics.FRAME_PX, frame.width)
            assertEquals("frame $i height", SpriteMetrics.FRAME_PX, frame.height)
            assertTrue("frame $i outside the png", frame.x + frame.width <= png.width && frame.y + frame.height <= png.height)
        }
    }

    @Test
    fun `tag ranges are valid and every frame has a positive duration`() {
        val sheet = loadSheet()
        sheet.tags.values.forEach { tag ->
            assertTrue("tag ${tag.name} range", tag.from in 0..tag.to && tag.to < sheet.frames.size)
        }
        sheet.frames.forEachIndexed { i, frame -> assertTrue("frame $i duration", frame.durationMs > 0) }
    }

    @Test
    fun `every pose the code asks for exists in likka poses json`() {
        val poses = loadSheet().poses.keys
        val requested = LikkaPose.entries.map { it.key } + MotionPose.entries.map { it.key }
        requested.forEach { key -> assertTrue("pose $key missing from likka_poses.json", key in poses) }
    }

    @Test
    fun `mirrored poses reuse the diagonal walk`() {
        val poses = loadSheet().poses
        listOf("walk_left", "walk_diag_down_left").forEach { key ->
            val spec = poses.getValue(key)
            assertEquals("walk_diag_down_right", spec.tag)
            assertTrue("$key mirror", spec.mirror)
        }
    }

    @Test
    fun `assets are an unchanged copy of the root sprites folder`() {
        loadSheet()
        val rootDir = File("../sprites")
        assumeTrue("Skipped: ${rootDir.absolutePath} does not exist", rootDir.isDirectory)
        listOf("likka.png", "likka.json", "likka_poses.json").forEach { name ->
            assertArrayEquals("$name differs from sprites/$name", File(rootDir, name).readBytes(), File(assetsDir, name).readBytes())
        }
    }

    private companion object {
        val SHEET_TAGS =
            setOf("idle", "walk_down", "walk_up", "walk_diag_down_right", "annoyed", "fury", "peek", "sit", "sleep", "look_around")
    }
}
