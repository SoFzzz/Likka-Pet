package com.likkapet.service

import com.likkapet.domain.model.LikkaOverlayState
import com.likkapet.domain.model.LikkaState

/**
 * The diagnostic line for a change of global state, level or reason, used while no overlay exists.
 * It carries enum names and numbers only: never an app name or package, never any roast text.
 */
object StateChangeLog {
    /** What a line is made of; the per-second Level 3 countdown is left out so it does not log every tick. */
    data class Entry(
        val global: LikkaState,
        val level: Int,
        val reason: String,
        val visibility: String,
        val isPaused: Boolean,
        val isFarewell: Boolean,
    )

    fun entryOf(
        global: LikkaState,
        overlay: LikkaOverlayState,
    ) = Entry(
        global = global,
        level = overlay.level,
        reason = overlay.reason?.name ?: "NONE",
        visibility = overlay.visibility.name,
        isPaused = overlay.isPaused,
        isFarewell = overlay.isFarewell,
    )

    fun format(entry: Entry): String =
        "State: global=${entry.global} level=${entry.level} reason=${entry.reason} " +
            "visibility=${entry.visibility} paused=${entry.isPaused} farewell=${entry.isFarewell}"
}
