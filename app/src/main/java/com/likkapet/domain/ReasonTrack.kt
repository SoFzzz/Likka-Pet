package com.likkapet.domain

import com.likkapet.domain.model.ReasonLevel

/**
 * Level and Level 1→2→3 escalation timer of one reason (documentación §3.3). Time only moves when
 * the coordinator calls [advance], which is how freezing (away, suspended, paused, on table) works.
 */
internal class ReasonTrack {
    var level = ReasonLevel.NONE
        private set

    /** When this reason last went from inactive to active; breaks level ties (§3.3). */
    var activeSinceMs: Long? = null
        private set

    private var elapsedInLevelMs = 0L

    val isActive: Boolean get() = level != ReasonLevel.NONE

    fun activate(
        level: ReasonLevel,
        activeSinceMs: Long,
    ) {
        this.level = level
        this.activeSinceMs = activeSinceMs
        elapsedInLevelMs = 0L
    }

    /** Adds active time, carrying any overflow into the next level so one long step can cross several. */
    fun advance(deltaMs: Long) {
        if (!isActive) return
        elapsedInLevelMs += deltaMs
        var msToNextLevel = msToLeave(level)
        while (msToNextLevel != null && elapsedInLevelMs >= msToNextLevel) {
            elapsedInLevelMs -= msToNextLevel
            level = ReasonLevel.entries[level.ordinal + 1]
            msToNextLevel = msToLeave(level)
        }
    }

    fun reset() {
        level = ReasonLevel.NONE
        activeSinceMs = null
        elapsedInLevelMs = 0L
    }

    private fun msToLeave(level: ReasonLevel): Long? =
        when (level) {
            ReasonLevel.LEVEL_1 -> EscalationTimings.LEVEL_1_TO_2_MS
            ReasonLevel.LEVEL_2 -> EscalationTimings.LEVEL_2_TO_3_MS
            ReasonLevel.NONE, ReasonLevel.LEVEL_3 -> null
        }
}
