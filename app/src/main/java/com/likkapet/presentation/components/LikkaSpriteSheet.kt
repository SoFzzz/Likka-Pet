package com.likkapet.presentation.components

import org.json.JSONObject

/** One cell of `likka.png`, as `likka.json` (Aseprite Array export) describes it. */
data class SpriteFrame(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val durationMs: Long,
)

/** An Aseprite tag: an inclusive range of frame indices. */
data class SpriteTag(
    val name: String,
    val from: Int,
    val to: Int,
)

/** Playback `mode` of a pose in `likka_poses.json` (design system §1.7). */
enum class PlaybackMode(
    val key: String,
) {
    LOOP("loop"),
    ONCE("once"),
    ONCE_THEN_IDLE("once_then_idle"),
    HOLD("hold"),
    ;

    companion object {
        fun fromKey(key: String): PlaybackMode =
            entries.firstOrNull { it.key == key } ?: throw IllegalArgumentException("Unknown playback mode: $key")
    }
}

/** One entry of `likka_poses.json`: which tag a pose plays, how, and its Compose extras. */
data class PoseSpec(
    val tag: String,
    val mode: PlaybackMode,
    val mirror: Boolean,
    val extras: List<String>,
)

/** The frames a pose plays, ready to draw, with the Compose extras drawn around them. */
data class SpriteClip(
    val frames: List<SpriteFrame>,
    val mode: PlaybackMode,
    val mirror: Boolean,
    val extras: Set<SpriteExtra> = emptySet(),
)

/**
 * `likka.json` + `likka_poses.json` (design system §1.7). Frame positions and durations come only
 * from the JSON, never from hardcoded offsets.
 */
data class LikkaSpriteSheet(
    val frames: List<SpriteFrame>,
    val tags: Map<String, SpriteTag>,
    val poses: Map<String, PoseSpec>,
) {
    /** The clip of [poseKey], or null when the pose or its tag is missing. */
    fun clipFor(poseKey: String): SpriteClip? {
        val pose = poses[poseKey] ?: return null
        val tag = tags[pose.tag] ?: return null
        return SpriteClip(frames.subList(tag.from, tag.to + 1), pose.mode, pose.mirror, SpriteExtra.fromKeys(pose.extras))
    }

    companion object {
        const val IDLE_POSE = "idle"

        fun parse(
            sheetJson: String,
            posesJson: String,
        ): LikkaSpriteSheet {
            val sheet = JSONObject(sheetJson)
            return LikkaSpriteSheet(
                frames = parseFrames(sheet),
                tags = parseTags(sheet),
                poses = parsePoses(JSONObject(posesJson)),
            )
        }

        private fun parseFrames(sheet: JSONObject): List<SpriteFrame> {
            val frames = sheet.getJSONArray("frames")
            return List(frames.length()) { i ->
                val entry = frames.getJSONObject(i)
                val rect = entry.getJSONObject("frame")
                SpriteFrame(
                    x = rect.getInt("x"),
                    y = rect.getInt("y"),
                    width = rect.getInt("w"),
                    height = rect.getInt("h"),
                    durationMs = entry.getLong("duration"),
                )
            }
        }

        private fun parseTags(sheet: JSONObject): Map<String, SpriteTag> {
            val tags = sheet.getJSONObject("meta").getJSONArray("frameTags")
            return List(tags.length()) { i ->
                val tag = tags.getJSONObject(i)
                SpriteTag(tag.getString("name"), tag.getInt("from"), tag.getInt("to"))
            }.associateBy { it.name }
        }

        private fun parsePoses(poses: JSONObject): Map<String, PoseSpec> =
            poses.keys().asSequence().associateWith { key ->
                val pose = poses.getJSONObject(key)
                val extras = pose.optJSONArray("extras")
                PoseSpec(
                    tag = pose.getString("tag"),
                    mode = PlaybackMode.fromKey(pose.getString("mode")),
                    mirror = pose.optBoolean("mirror", false),
                    extras = extras?.let { array -> List(array.length()) { array.getString(it) } } ?: emptyList(),
                )
            }
    }
}

/** Where playback is: which clip and which frame of it. */
data class PlaybackPosition(
    val clip: SpriteClip,
    val frameIndex: Int,
)

/**
 * The next position after the current frame's duration, or null when playback stops
 * (`hold`, the end of `once`). `once_then_idle` continues with [idleClip] in loop.
 */
fun nextPlaybackPosition(
    current: PlaybackPosition,
    idleClip: SpriteClip?,
): PlaybackPosition? {
    val clip = current.clip
    val next = current.frameIndex + 1
    val idleLoop = idleClip?.copy(mode = PlaybackMode.LOOP)
    return when {
        clip.mode == PlaybackMode.HOLD -> null
        next < clip.frames.size -> current.copy(frameIndex = next)
        clip.mode == PlaybackMode.LOOP -> current.copy(frameIndex = 0)
        clip.mode == PlaybackMode.ONCE_THEN_IDLE && idleLoop != null -> PlaybackPosition(idleLoop, frameIndex = 0)
        else -> null
    }
}
