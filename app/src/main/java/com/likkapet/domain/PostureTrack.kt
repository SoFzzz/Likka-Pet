package com.likkapet.domain

import com.likkapet.domain.model.PostureReading
import com.likkapet.domain.model.ReasonLevel

/**
 * POSTURE track (documentación §3.1–§3.3): trigger/forgive hysteresis, Grace (rule B), relapse
 * (rule C) and the on-table freeze (§4.1). Grace and the relapse window run on wall-clock time;
 * the continuous-posture counters restart whenever detection is interrupted.
 */
internal class PostureTrack(
    // false: once triggered, the track only ends with the session (5 min away) or an ejection,
    // however the phone is held afterwards (owner decision, 2026-10-05).
    private val forgivesGoodPosture: Boolean = true,
) {
    val track = ReasonTrack()

    var isOnTable = false
        private set

    private var zone = PostureZone.UNKNOWN
    private var badPostureMs = 0L
    private var goodPostureMs = 0L
    private var graceEndsAtMs: Long? = null
    private var relapseDeadlineMs: Long? = null
    private var levelAtLastResolution = ReasonLevel.NONE

    /**
     * Level that counts towards the overlay. Option B (§4.1): once a level is active, the table
     * does not hide it — it only prevents a new trigger. This stops the overlay from flickering
     * between Withdrawn and Companion when the phone is held nearly flat.
     */
    val shownLevel: ReasonLevel get() = if (isOnTable && !track.isActive) ReasonLevel.NONE else track.level

    fun onReading(reading: PostureReading) {
        isOnTable = reading.isOnTable
        val newZone = if (reading.isOnTable) PostureZone.UNKNOWN else PostureZone.of(reading.angleDegrees)
        if (newZone != zone) resetContinuousCounters()
        zone = newZone
    }

    fun interruptDetection() {
        zone = PostureZone.UNKNOWN
        resetContinuousCounters()
    }

    fun advance(
        deltaMs: Long,
        nowMs: Long,
        isUsageTimeActive: Boolean,
    ): PostureOutcome {
        // Grace is checked at the start of the step so its last second is never counted as bad posture.
        if (isOnTable || isInGrace(nowMs - deltaMs)) return PostureOutcome.NONE
        return if (track.isActive) {
            advanceActive(deltaMs, nowMs, isUsageTimeActive)
        } else {
            advanceInactive(deltaMs, nowMs)
        }
    }

    /** Rule D: a reason still active when ejected comes back straight at Level 2. */
    fun resumeAtLevel2(activeSinceMs: Long) {
        track.activate(ReasonLevel.LEVEL_2, activeSinceMs)
        resetContinuousCounters()
    }

    fun clearLevel() = track.reset()

    fun endSession() {
        track.reset()
        graceEndsAtMs = null
        relapseDeadlineMs = null
        levelAtLastResolution = ReasonLevel.NONE
        interruptDetection()
    }

    private fun isInGrace(nowMs: Long): Boolean = graceEndsAtMs?.let { nowMs < it } ?: false

    private fun advanceInactive(
        deltaMs: Long,
        nowMs: Long,
    ): PostureOutcome {
        if (zone != PostureZone.BAD) return PostureOutcome.NONE
        badPostureMs += deltaMs
        if (badPostureMs < EscalationTimings.POSTURE_TRIGGER_MS) return PostureOutcome.NONE
        track.activate(levelOnTrigger(nowMs), nowMs)
        track.advance(badPostureMs - EscalationTimings.POSTURE_TRIGGER_MS)
        resetContinuousCounters()
        return PostureOutcome.TRIGGERED
    }

    private fun advanceActive(
        deltaMs: Long,
        nowMs: Long,
        isUsageTimeActive: Boolean,
    ): PostureOutcome {
        track.advance(deltaMs)
        if (!forgivesGoodPosture || zone != PostureZone.GOOD) return PostureOutcome.NONE
        goodPostureMs += deltaMs
        if (goodPostureMs < EscalationTimings.POSTURE_RESET_MS) return PostureOutcome.NONE
        resolve(nowMs, isUsageTimeActive)
        return if (isUsageTimeActive) PostureOutcome.RESOLVED else PostureOutcome.RESOLVED_WITH_GRACE
    }

    /** Rule C: relapsing inside the window resumes the level reached last time (minimum Level 1). */
    private fun levelOnTrigger(nowMs: Long): ReasonLevel {
        val isRelapse = relapseDeadlineMs?.let { nowMs < it } ?: false
        return if (isRelapse) maxOf(levelAtLastResolution, ReasonLevel.LEVEL_1) else ReasonLevel.LEVEL_1
    }

    /** Rule B: Grace only when USAGE_TIME is inactive; the rule C window starts either way. */
    private fun resolve(
        nowMs: Long,
        isUsageTimeActive: Boolean,
    ) {
        levelAtLastResolution = track.level
        relapseDeadlineMs = nowMs + EscalationTimings.RELAPSE_WINDOW_MS
        graceEndsAtMs = if (isUsageTimeActive) null else nowMs + EscalationTimings.GRACE_AFTER_RESET_MS
        track.reset()
        resetContinuousCounters()
    }

    private fun resetContinuousCounters() {
        badPostureMs = 0L
        goodPostureMs = 0L
    }
}

internal enum class PostureOutcome { NONE, TRIGGERED, RESOLVED, RESOLVED_WITH_GRACE }

/** Hysteresis bands: between the two angles posture neither triggers nor forgives. */
internal enum class PostureZone {
    UNKNOWN,
    BAD,
    NEUTRAL,
    GOOD,
    ;

    companion object {
        fun of(angleDegrees: Double): PostureZone =
            when {
                angleDegrees < EscalationConfig.POSTURE_DANGER_ANGLE -> BAD
                angleDegrees > EscalationConfig.POSTURE_RESET_ANGLE -> GOOD
                else -> NEUTRAL
            }
    }
}
