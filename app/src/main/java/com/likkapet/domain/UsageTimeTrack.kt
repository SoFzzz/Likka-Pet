package com.likkapet.domain

import com.likkapet.domain.model.ReasonLevel

/**
 * USAGE_TIME track (documentación §3.1): the leisure session accumulates across all watched apps
 * and only ends after [EscalationConfig.SESSION_END_AWAY_MIN] away (rule A: posture never resolves it).
 */
internal class UsageTimeTrack {
    val track = ReasonTrack()

    private var sessionUsageMs = 0L

    /** Called only while the coordinator is actively watching; everything else freezes the session. */
    fun advance(
        deltaMs: Long,
        nowMs: Long,
    ) {
        sessionUsageMs += deltaMs
        if (track.isActive) {
            track.advance(deltaMs)
        } else if (sessionUsageMs >= EscalationTimings.USAGE_THRESHOLD_MS) {
            track.activate(ReasonLevel.LEVEL_1, nowMs)
            // Only the part of this step past the threshold counts, even if an earlier session
            // part already exceeded it (level cleared by an ejection without rule D).
            track.advance(minOf(deltaMs, sessionUsageMs - EscalationTimings.USAGE_THRESHOLD_MS))
        }
    }

    /** Rule D: a reason still active when ejected comes back straight at Level 2. */
    fun resumeAtLevel2(activeSinceMs: Long) = track.activate(ReasonLevel.LEVEL_2, activeSinceMs)

    /** Ejection clears the level but not the session: the session only ends after time away. */
    fun clearLevel() = track.reset()

    fun endSession() {
        track.reset()
        sessionUsageMs = 0L
    }
}
