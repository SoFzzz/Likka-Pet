package com.likkapet.domain

import com.likkapet.domain.model.LikkaOverlayState
import com.likkapet.domain.model.LikkaState
import com.likkapet.domain.model.OverlayVisibility
import com.likkapet.domain.model.PauseResult
import com.likkapet.domain.model.PostureReading
import com.likkapet.domain.model.ReasonLevel
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.TriggerReason
import com.likkapet.domain.port.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State machine of documentación §3.3: a global [LikkaState] plus two independent reason tracks.
 *
 * It is synchronous and never schedules work: every event first advances all running timers to
 * [Clock.nowMillis], then applies the event. The service must call [onTick] periodically (about
 * once per second) so timers fire without other events; tests move a fake clock and do the same.
 * Thresholds are applied with the precision of that tick. Repeated events with an unchanged value
 * are harmless, so sources may report on every poll instead of only on changes.
 */
class EscalationCoordinator(
    private val clock: Clock,
    // Owner decision (2026-10-05): good posture no longer sends Likka away; see EscalationConfig.
    forgivesGoodPosture: Boolean = EscalationConfig.POSTURE_FORGIVES_GOOD_POSTURE,
) {
    private val posture = PostureTrack(forgivesGoodPosture)
    private val usageTime = UsageTimeTrack()

    // Global state before applying the SUSPENDED overlay; see [reportedState].
    private var baseState = LikkaState.IDLE
    private var foregroundApp: TargetApp? = null
    private var lastWatchedApp: TargetApp? = null
    private var isInCall = false

    // When the last call ended, while its grace runs (EscalationConfig.CALL_END_GRACE_SEC); cleared
    // when the grace runs out, a watched app is back in front or another call starts.
    private var callEndedAtMs: Long? = null
    private var isScreenOff = false
    private var awayMs = 0L
    private var level3ElapsedMs = 0L
    private var farewellEndsAtMs: Long? = null
    private var pausedUntilMs: Long? = null
    private var stateAfterPause = LikkaState.IDLE
    private var activeSinceAtEjection: Map<TriggerReason, Long> = emptyMap()

    // Rule D needs a real exit: a poll that still reports the watched app right after the
    // ejection (before HomeLauncher takes effect) is not a return, unless it keeps being reported
    // for longer than EJECTION_EXIT_TIMEOUT_SEC.
    private var hasLeftSinceEjection = false
    private var stayedAfterEjectionMs = 0L
    private var lastUpdateMs = clock.nowMillis()

    private val mutableOverlayState = MutableStateFlow(LikkaOverlayState.NOTHING_TO_SHOW)
    val overlayState: StateFlow<LikkaOverlayState> = mutableOverlayState.asStateFlow()

    private val mutableLikkaState = MutableStateFlow(LikkaState.IDLE)
    val likkaState: StateFlow<LikkaState> = mutableLikkaState.asStateFlow()

    @Synchronized
    fun onTick() = update { }

    @Synchronized
    fun onPostureReading(reading: PostureReading) = update { if (isRunning) posture.onReading(reading) }

    /**
     * [app] is null when the foreground app is not a watched one (launcher, other apps, Likka).
     * The source should not report the keyguard or screen-off pauses as leaving the app: screen off
     * is signalled only through [onScreenStateChanged], otherwise Level 3 would count it as an exit.
     *
     * Single-field form for tests; a foreground source must use [onForegroundStateChanged], since
     * applying the app and the call one at a time can turn a call at Level 3 into an ejection.
     */
    @Synchronized
    internal fun onForegroundAppChanged(app: TargetApp?) = update { changeForegroundApp(app) }

    /** Single-field form for tests; see [onForegroundAppChanged]. */
    @Synchronized
    internal fun onCallStateChanged(isInCall: Boolean) = update { changeCallState(isInCall) }

    /**
     * One poll of the foreground source: the call state and the foreground app applied together and
     * reconciled once. A call screen covering the watched app at Level 3 arrives as "no watched app"
     * plus "in call", and hanging up as the reverse; applied one at a time, either order leaves an
     * instant at Level 3 outside the app and unsuspended, which would count as an ejection.
     */
    @Synchronized
    fun onForegroundStateChanged(
        app: TargetApp?,
        isInCall: Boolean,
    ) = update {
        changeCallState(isInCall)
        changeForegroundApp(app)
    }

    @Synchronized
    fun onScreenStateChanged(isScreenOn: Boolean) = update { changeScreenState(isScreenOff = !isScreenOn) }

    @Synchronized
    fun onSurrenderClick() = update { if (isRunning && shownLevel() == ReasonLevel.LEVEL_3) eject() }

    /**
     * Rule E (documentación §3.2, RF-S01). [pausesUsedToday] comes from the stats store; the caller
     * increments `pauses_today` only when this returns [PauseResult.ACCEPTED].
     */
    @Synchronized
    fun onPauseSelected(
        minutes: Int,
        pausesUsedToday: Int,
    ): PauseResult {
        require(minutes in EscalationConfig.PAUSE_OPTIONS_MIN) { "Unsupported pause length: $minutes min" }
        return update { pauseRejection(pausesUsedToday) ?: startPause(minutes * EscalationTimings.MILLIS_PER_MINUTE) }
    }

    /**
     * Puts a pause that was already accepted (and counted) back, with [remainingMs] left: the service
     * restarted while `paused_until` was still in the future. Skips rule E on purpose, since the
     * pause was already counted when it started. Does nothing unless no pause or ejection is running.
     */
    @Synchronized
    fun onPauseRestored(remainingMs: Long) =
        update {
            if (baseState == LikkaState.IDLE ||
                baseState == LikkaState.WATCHING
            ) {
                startPause(remainingMs)
            }
        }

    @Synchronized
    fun onResumeClick() = update { if (baseState == LikkaState.PAUSED) endPause() }

    /** Per-track level, including a track hidden on the table; for tests and diagnostics. */
    @Synchronized
    internal fun trackLevel(reason: TriggerReason): ReasonLevel =
        when (reason) {
            TriggerReason.POSTURE -> posture.track.level
            TriggerReason.USAGE_TIME -> usageTime.track.level
        }

    // A call, or the few seconds after it while the user is still away (the call screen or the
    // VoIP app in front): both freeze everything.
    private val isCallFreezing: Boolean get() = isInCall || (callEndedAtMs != null && foregroundApp == null)

    private val isSuspended: Boolean get() = isCallFreezing || isScreenOff

    // Screen off counts as time away for ending a session (documentación §3.3).
    private val isAway: Boolean get() = foregroundApp == null || isScreenOff

    private val isRunning: Boolean
        get() = baseState == LikkaState.WATCHING && foregroundApp != null && !isSuspended

    private val reportedState: LikkaState
        get() = if (baseState == LikkaState.WATCHING && isSuspended) LikkaState.SUSPENDED else baseState

    private inline fun <T> update(event: () -> T): T {
        advanceTo(clock.nowMillis())
        val result = event()
        reconcile()
        publish()
        return result
    }

    // The pause end and the end of the grace after a call are applied in the order they happened,
    // even when one late update crosses both: the time between them must count by the right rules.
    // The grace ends on a tick even if no poll arrives: still away at Level 3, that is the exit.
    private fun advanceTo(nowMs: Long) {
        while (true) {
            val pauseEndMs = pausedUntilMs?.takeIf { baseState == LikkaState.PAUSED && it <= nowMs }
            val graceEndMs = callEndedAtMs?.plus(EscalationTimings.CALL_END_GRACE_MS)?.takeIf { it <= nowMs }
            when {
                pauseEndMs != null && (graceEndMs == null || pauseEndMs <= graceEndMs) -> {
                    advanceSegment(pauseEndMs)
                    endPause()
                }

                graceEndMs != null -> {
                    advanceSegment(graceEndMs)
                    callEndedAtMs = null
                }

                else -> {
                    break
                }
            }
            reconcile()
        }
        advanceSegment(nowMs)
    }

    private fun advanceSegment(untilMs: Long) {
        val deltaMs = untilMs - lastUpdateMs
        if (deltaMs <= 0L) return
        lastUpdateMs = untilMs
        advanceAwayTime(deltaMs)
        advanceStayAfterEjection(deltaMs)
        if (isRunning) advanceRunningTimers(deltaMs, untilMs)
    }

    // A call freezes everything, including the countdown to the end of the session. While ejected
    // the window runs even if the watched app is still reported (rule D counts from the ejection).
    private fun advanceAwayTime(deltaMs: Long) {
        val isCountingAway = isAway || baseState == LikkaState.EJECTED
        if (!isCountingAway || isCallFreezing || baseState == LikkaState.IDLE) return
        awayMs += deltaMs
        if (awayMs >= EscalationTimings.SESSION_END_AWAY_MS) endSession()
    }

    private fun advanceStayAfterEjection(deltaMs: Long) {
        val isStillInWatchedApp = foregroundApp != null && !isSuspended
        if (baseState == LikkaState.EJECTED && !hasLeftSinceEjection && isStillInWatchedApp) {
            stayedAfterEjectionMs += deltaMs
        }
    }

    private fun advanceRunningTimers(
        deltaMs: Long,
        nowMs: Long,
    ) {
        val wasShowingLevel3 = shownLevel() == ReasonLevel.LEVEL_3
        usageTime.advance(deltaMs, nowMs)
        val outcome = posture.advance(deltaMs, nowMs, usageTime.track.isActive)
        if (outcome == PostureOutcome.RESOLVED_WITH_GRACE) {
            farewellEndsAtMs = nowMs + EscalationTimings.FAREWELL_MS
        }
        advanceLevel3Countdown(deltaMs, wasShowingLevel3)
    }

    // The countdown survives while a Level 3 track is shown, and resets once no track is at Level 3
    // any more.
    private fun advanceLevel3Countdown(
        deltaMs: Long,
        wasShowingLevel3: Boolean,
    ) {
        if (!hasTrackAtLevel3()) {
            level3ElapsedMs = 0L
            return
        }
        if (!wasShowingLevel3 || shownLevel() != ReasonLevel.LEVEL_3) return
        level3ElapsedMs += deltaMs
        if (level3ElapsedMs >= EscalationTimings.LEVEL_3_AUTO_HOME_MS) eject()
    }

    private fun changeForegroundApp(app: TargetApp?) {
        foregroundApp = app
        if (app != null) {
            lastWatchedApp = app
            callEndedAtMs = null
        } else {
            hasLeftSinceEjection = true
            posture.interruptDetection()
        }
    }

    private fun changeCallState(isInCall: Boolean) {
        if (this.isInCall == isInCall) return
        this.isInCall = isInCall
        callEndedAtMs = if (isInCall) null else lastUpdateMs
        posture.interruptDetection()
    }

    private fun changeScreenState(isScreenOff: Boolean) {
        if (this.isScreenOff == isScreenOff) return
        this.isScreenOff = isScreenOff
        posture.interruptDetection()
    }

    private fun reconcile() {
        when (baseState) {
            LikkaState.IDLE -> if (!isAway) startSession()
            LikkaState.EJECTED -> if (hasReturnedAfterEjection()) returnAfterEjection()
            LikkaState.WATCHING -> if (hasLeftAtLevel3()) eject()
            LikkaState.PAUSED, LikkaState.SUSPENDED -> Unit
        }
        if (!isAway && baseState != LikkaState.EJECTED) awayMs = 0L
    }

    private fun hasReturnedAfterEjection(): Boolean {
        val hasStayedTooLong = stayedAfterEjectionMs >= EscalationTimings.EJECTION_EXIT_TIMEOUT_MS
        return !isAway && (hasLeftSinceEjection || hasStayedTooLong)
    }

    // Any exit from a watched app at global Level 3 is an ejection (RF-O03), not HIDDEN_WHILE_AWAY.
    private fun hasLeftAtLevel3(): Boolean = foregroundApp == null && !isSuspended && hasTrackAtLevel3()

    private fun startSession() {
        baseState = LikkaState.WATCHING
        posture.interruptDetection()
    }

    // Rule D: inside the window, every reason active at ejection resumes at Level 2.
    private fun returnAfterEjection() {
        if (awayMs < EscalationTimings.QUICK_RETURN_WINDOW_MS) {
            activeSinceAtEjection[TriggerReason.POSTURE]?.let(posture::resumeAtLevel2)
            activeSinceAtEjection[TriggerReason.USAGE_TIME]?.let(usageTime::resumeAtLevel2)
        }
        activeSinceAtEjection = emptyMap()
        baseState = LikkaState.WATCHING
    }

    private fun eject() {
        activeSinceAtEjection = activeSinceByReason()
        posture.clearLevel()
        usageTime.clearLevel()
        posture.interruptDetection()
        level3ElapsedMs = 0L
        farewellEndsAtMs = null
        awayMs = 0L
        // Leaving at Level 3 has already happened; otherwise HomeLauncher is about to do it.
        hasLeftSinceEjection = foregroundApp == null
        stayedAfterEjectionMs = 0L
        baseState = LikkaState.EJECTED
    }

    private fun endSession() {
        posture.endSession()
        usageTime.endSession()
        level3ElapsedMs = 0L
        farewellEndsAtMs = null
        activeSinceAtEjection = emptyMap()
        lastWatchedApp = null
        awayMs = 0L
        if (baseState == LikkaState.PAUSED) stateAfterPause = LikkaState.IDLE else baseState = LikkaState.IDLE
    }

    private fun pauseRejection(pausesUsedToday: Int): PauseResult? =
        PauseRules.rejectionFor(
            isPaused = baseState == LikkaState.PAUSED,
            isEjected = baseState == LikkaState.EJECTED,
            hasTrackAtLevel3 = hasTrackAtLevel3(),
            pausesUsedToday = pausesUsedToday,
        )

    private fun startPause(durationMs: Long): PauseResult {
        stateAfterPause = baseState
        pausedUntilMs = lastUpdateMs + durationMs
        baseState = LikkaState.PAUSED
        farewellEndsAtMs = null
        posture.interruptDetection()
        return PauseResult.ACCEPTED
    }

    private fun endPause() {
        baseState = stateAfterPause
        pausedUntilMs = null
        posture.interruptDetection()
    }

    private fun hasTrackAtLevel3(): Boolean = posture.track.level == ReasonLevel.LEVEL_3 || usageTime.track.level == ReasonLevel.LEVEL_3

    private fun shownLevel(): ReasonLevel = maxOf(posture.shownLevel, usageTime.track.level)

    // Tie → the reason that has been active the longest (documentación §3.3).
    private fun drivingReason(): TriggerReason {
        val postureLevel = posture.shownLevel
        val usageLevel = usageTime.track.level
        if (postureLevel != usageLevel) {
            return if (postureLevel > usageLevel) TriggerReason.POSTURE else TriggerReason.USAGE_TIME
        }
        val postureSince = posture.track.activeSinceMs ?: Long.MAX_VALUE
        val usageSince = usageTime.track.activeSinceMs ?: Long.MAX_VALUE
        return if (postureSince <= usageSince) TriggerReason.POSTURE else TriggerReason.USAGE_TIME
    }

    private fun activeSinceByReason(): Map<TriggerReason, Long> =
        buildMap {
            posture.track.activeSinceMs?.let { put(TriggerReason.POSTURE, it) }
            usageTime.track.activeSinceMs?.let { put(TriggerReason.USAGE_TIME, it) }
        }

    private fun publish() {
        mutableLikkaState.value = reportedState
        mutableOverlayState.value = buildOverlayState()
    }

    private fun buildOverlayState(): LikkaOverlayState {
        val isPaused = baseState == LikkaState.PAUSED
        val level = if (isPaused) ReasonLevel.NONE else shownLevel()
        val hasLevel = level != ReasonLevel.NONE
        return LikkaOverlayState(
            visibility = visibility(),
            level = level.value,
            reason = if (hasLevel) drivingReason() else null,
            app = foregroundApp ?: lastWatchedApp,
            roast = null,
            level3SecondsLeft = if (level == ReasonLevel.LEVEL_3) level3SecondsLeft() else null,
            isPaused = isPaused,
            isFarewell = !hasLevel && !isPaused && isFarewellPlaying(),
            isWatchedAppInForeground = foregroundApp != null,
        )
    }

    // A pause also hides during a call or with the screen off, so the `sit` pose never covers
    // the call screen, even though the global state stays PAUSED.
    private fun visibility(): OverlayVisibility =
        when {
            isSuspended && (baseState == LikkaState.WATCHING || baseState == LikkaState.PAUSED) -> {
                OverlayVisibility.HIDDEN_SUSPENDED
            }

            isAway && (baseState == LikkaState.WATCHING || baseState == LikkaState.PAUSED) -> {
                OverlayVisibility.HIDDEN_WHILE_AWAY
            }

            else -> {
                OverlayVisibility.SHOWN
            }
        }

    private fun level3SecondsLeft(): Int {
        val remainingMs = EscalationTimings.LEVEL_3_AUTO_HOME_MS - level3ElapsedMs
        return ((remainingMs + EscalationTimings.MILLIS_PER_SECOND - 1) / EscalationTimings.MILLIS_PER_SECOND).toInt()
    }

    private fun isFarewellPlaying(): Boolean = farewellEndsAtMs?.let { lastUpdateMs < it } ?: false
}
