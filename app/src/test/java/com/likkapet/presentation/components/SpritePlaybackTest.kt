package com.likkapet.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Playback modes of `likka_poses.json` (design system §1.7) and parsing of the Aseprite JSON. */
class SpritePlaybackTest {
    private fun clip(
        mode: PlaybackMode,
        frameCount: Int = 3,
    ) = SpriteClip(List(frameCount) { SpriteFrame(it * 96, 0, 96, 96, 100) }, mode, mirror = false)

    @Test
    fun `loop wraps to the first frame`() {
        val loop = clip(PlaybackMode.LOOP)
        assertEquals(1, nextPlaybackPosition(PlaybackPosition(loop, 0), idleClip = null)?.frameIndex)
        assertEquals(0, nextPlaybackPosition(PlaybackPosition(loop, 2), idleClip = null)?.frameIndex)
    }

    @Test
    fun `once stops on the last frame`() {
        val once = clip(PlaybackMode.ONCE)
        assertEquals(2, nextPlaybackPosition(PlaybackPosition(once, 1), idleClip = null)?.frameIndex)
        assertNull(nextPlaybackPosition(PlaybackPosition(once, 2), idleClip = null))
    }

    @Test
    fun `hold never advances`() {
        assertNull(nextPlaybackPosition(PlaybackPosition(clip(PlaybackMode.HOLD), 0), idleClip = null))
    }

    @Test
    fun `once then idle continues with idle in loop`() {
        val idle = clip(PlaybackMode.LOOP, frameCount = 8)
        val next = nextPlaybackPosition(PlaybackPosition(clip(PlaybackMode.ONCE_THEN_IDLE), 2), idle)
        assertEquals(PlaybackPosition(idle, 0), next)
    }

    @Test
    fun `parse reads frames, durations, tags and poses`() {
        val sheet = LikkaSpriteSheet.parse(SHEET_JSON, POSES_JSON)
        assertEquals(SpriteFrame(96, 0, 96, 96, 125), sheet.frames[1])
        assertEquals(SpriteTag("idle", 0, 1), sheet.tags["idle"])
        assertEquals(PoseSpec("idle", PlaybackMode.LOOP, mirror = true, extras = listOf("sparkles")), sheet.poses["happy"])
        assertEquals(2, sheet.clipFor("happy")?.frames?.size)
        assertNull(sheet.clipFor("missing"))
    }

    private companion object {
        const val SHEET_JSON = """
            {"frames": [
              {"frame": {"x": 0, "y": 0, "w": 96, "h": 96}, "duration": 125},
              {"frame": {"x": 96, "y": 0, "w": 96, "h": 96}, "duration": 125}
            ], "meta": {"frameTags": [{"name": "idle", "from": 0, "to": 1, "direction": "forward"}]}}
        """
        const val POSES_JSON = """
            {"happy": {"tag": "idle", "mode": "loop", "mirror": true, "extras": ["sparkles"]}}
        """
    }
}
