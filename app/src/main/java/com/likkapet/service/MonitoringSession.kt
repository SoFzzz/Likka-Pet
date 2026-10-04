package com.likkapet.service

import com.likkapet.data.usage.DailyStatsRecorder
import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.EscalationCoordinator
import com.likkapet.domain.model.ForegroundState
import com.likkapet.domain.model.LikkaOverlayState
import com.likkapet.domain.model.LikkaState
import com.likkapet.domain.model.OverlayFeed
import com.likkapet.domain.model.PauseResult
import com.likkapet.domain.model.PostureReading
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.TriggerReason
import com.likkapet.domain.port.Clock
import com.likkapet.domain.port.ForegroundAppSource
import com.likkapet.domain.port.PostureSource
import com.likkapet.domain.port.RoastGenerator
import com.likkapet.domain.port.ScreenStateSource
import com.likkapet.domain.port.StatsStore
import com.likkapet.domain.port.WallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/**
 * One run of the monitoring service: sensors, foreground poller and screen state feed the
 * [EscalationCoordinator], whose state, with the roast to show attached, is [overlayState]: the
 * only thing the overlay reads. The overlay only sends events back, like [onSurrenderClick]
 * (documentación §5, §10). Android-free, so JVM tests drive it with
 * fakes and a virtual clock. The service creates one per start and calls [stop] when it ends.
 *
 * Besides feeding the coordinator it ticks it once per second (§3.3 contract), records the daily
 * stats, asks for the Level 1 roasts when a watched app opens (RF-I10), takes one roast per level
 * reached and prefetches the next level's (§6 Módulo 4), rolls the day over at midnight and puts
 * back a pause that was still running when the service restarted.
 */
class MonitoringSession(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val wallClock: WallClock,
    private val zone: () -> ZoneId,
    private val store: StatsStore,
    private val postureSource: PostureSource,
    private val foregroundAppSource: ForegroundAppSource,
    private val screenStateSource: ScreenStateSource,
    private val roastGenerator: RoastGenerator,
    private val recorder: DailyStatsRecorder,
    private val log: (String) -> Unit = {},
) {
    private val coordinator = EscalationCoordinator(clock)
    private var runJob: Job? = null
    private val readingsSinceLastTake = AtomicInteger()
    private var lastPrefetchedApp: TargetApp? = null
    private var lastLoggedForeground: ForegroundState? = null
    private var shownRoast: ShownRoast? = null
    private var lastLoggedOnTable: Boolean? = null

    // How the phone is held right now, with the same 45°/55° hysteresis as the POSTURE track: true
    // hunched, false upright, null not known yet. It picks the roast's topic (owner decision,
    // 2026-10-05): about the neck while hunched, about the time in the app otherwise.
    private val isHunched = MutableStateFlow<Boolean?>(null)

    private val mutableOverlayState = MutableStateFlow(LikkaOverlayState.NOTHING_TO_SHOW)

    /** The coordinator's state with [LikkaOverlayState.roast] filled in: one roast per level, reason and app. */
    val overlayState: StateFlow<LikkaOverlayState> = mutableOverlayState.asStateFlow()

    private val mutableOverlayFeed = MutableStateFlow(OverlayFeed.INITIAL)

    /** What the overlay reads: [likkaState] and [overlayState] from the same coordinator publication. */
    val overlayFeed: StateFlow<OverlayFeed> = mutableOverlayFeed.asStateFlow()
    val likkaState: StateFlow<LikkaState> = coordinator.likkaState

    /** Begins feeding the coordinator and the stats; call once. */
    fun start() {
        // The poller starts a poll loop per collector, so the coordinator and the stats share one.
        val foreground = foregroundAppSource.states.shareIn(scope, SharingStarted.WhileSubscribed())
        recorder.start(foreground, screenStateSource.isScreenOn, coordinator.overlayState)
        // The pause goes back first, before any event can move the coordinator out of IDLE/WATCHING.
        runJob =
            scope.launch {
                restorePause()
                supervisorScope {
                    launch { postureSource.readings.collect(::onPostureReading) }
                    launch { foreground.collect(::onForegroundState) }
                    launch { screenStateSource.isScreenOn.collect(coordinator::onScreenStateChanged) }
                    launch { tickCoordinator() }
                    launch { attachRoasts() }
                    launch { rollDayOver() }
                    launch { logStateChanges() }
                }
            }
    }

    /** Stops listening and writes the minutes still in memory; safe to call twice. */
    suspend fun stop() {
        runJob?.cancelAndJoin()
        runJob = null
        recorder.stop()
    }

    /** Rule E through the live coordinator; the pause is persisted only when it is accepted. */
    suspend fun requestPause(minutes: Int): PauseResult {
        val pausesUsedToday =
            store.snapshot
                .first()
                .today.pauses
        val result = coordinator.onPauseSelected(minutes, pausesUsedToday)
        if (result == PauseResult.ACCEPTED) persistAcceptedPause(minutes)
        return result
    }

    // If the write fails the pause still holds in memory, as the user asked, but a restart would lose
    // it and the daily counter is not incremented, so rule E can be bypassed once until the store recovers.
    private suspend fun persistAcceptedPause(minutes: Int) {
        try {
            store.startPause(minutes)
        } catch (e: IOException) {
            log("Pause not persisted: ${e::class.simpleName}")
        }
    }

    /** "Me rindo" on the Level 3 panel (RF-O03); ignored at any other level. */
    fun onSurrenderClick() = coordinator.onSurrenderClick()

    suspend fun resume() {
        coordinator.onResumeClick()
        try {
            store.resume()
        } catch (e: IOException) {
            log("Resume not persisted: ${e::class.simpleName}")
        }
    }

    /** Posture readings since the last call, for the service's heartbeat log. */
    fun takeReadingsCount(): Int = readingsSinceLastTake.getAndSet(0)

    private fun onPostureReading(reading: PostureReading) {
        readingsSinceLastTake.incrementAndGet()
        logTableChange(reading.isOnTable)
        updateHunched(reading)
        coordinator.onPostureReading(reading)
    }

    // On the table the phone says nothing about the neck, so the last known posture is kept.
    private fun updateHunched(reading: PostureReading) {
        if (reading.isOnTable) return
        when {
            reading.angleDegrees < EscalationConfig.POSTURE_DANGER_ANGLE -> isHunched.value = true
            reading.angleDegrees > EscalationConfig.POSTURE_RESET_ANGLE -> isHunched.value = false
        }
    }

    // Only transitions are logged, never personal data; confirms §4.1 on the Redmi.
    private fun logTableChange(isOnTable: Boolean) {
        if (isOnTable == lastLoggedOnTable) return
        lastLoggedOnTable = isOnTable
        log("onTable=$isOnTable")
    }

    private fun onForegroundState(state: ForegroundState) {
        logForegroundChange(state)
        // Both halves of the poll at once: a call screen over the watched app must suspend, not eject (§3.3, §4.2).
        coordinator.onForegroundStateChanged(state.app, state.isInCall)
        prefetchWhenWatchedAppOpens(state.app)
    }

    // Only the mapped TargetApp (OTHER for an added app) and the call flag are logged, never a package.
    private fun logForegroundChange(state: ForegroundState) {
        if (state == lastLoggedForeground) return
        lastLoggedForeground = state
        log("Foreground: app=${state.app} inCall=${state.isInCall}")
    }

    // RF-I10: either reason can fire first, so both Level 1 roasts are asked for. The generator
    // makes no request with AI off (RF-I09) or with a full pool, so repeating this is harmless.
    private fun prefetchWhenWatchedAppOpens(app: TargetApp?) {
        if (app != null && app != lastPrefetchedApp) {
            TriggerReason.entries.forEach { roastGenerator.prefetch(app, FIRST_LEVEL, it) }
        }
        lastPrefetchedApp = app
    }

    // Sequential on purpose: each state waits for its roast, so the overlay never shows a level with
    // an empty bubble. nextRoast never waits for the network (pool or local phrase).
    private suspend fun attachRoasts() {
        merge(coordinator.likkaState, coordinator.overlayState, isHunched).conflate().collect {
            val feed = currentFeed()
            val withRoast = feed.overlay.copy(roast = roastFor(feed.overlay))
            mutableOverlayState.value = withRoast
            mutableOverlayFeed.value = feed.copy(overlay = withRoast)
        }
    }

    // The coordinator writes its global state before its overlay state, so reading them in the
    // opposite order never pairs a new overlay state with an old global one: an ejection is always
    // seen with, or before, its level 0. Either flow emitting is only the trigger.
    private fun currentFeed(): OverlayFeed {
        val overlay = coordinator.overlayState.value
        return OverlayFeed(coordinator.likkaState.value, overlay)
    }

    // The same roast stays while the level, topic and app stay (also across SUSPENDED or leaving
    // the app); a new one is taken on every change. Level 0 forgets it.
    private suspend fun roastFor(state: LikkaOverlayState): String? {
        val app = state.app
        val reason = state.reason?.let(::roastTopicFor)
        if (state.level == NO_LEVEL || app == null || reason == null) {
            shownRoast = null
            return null
        }
        val slot = RoastSlot(app, state.level, reason)
        shownRoast?.takeIf { it.slot == slot }?.let { return it.text }
        val text = roastGenerator.nextRoast(app, state.level, reason)
        if (state.level < LAST_LEVEL) roastGenerator.prefetch(app, state.level + 1, reason)
        shownRoast = ShownRoast(slot, text)
        return text
    }

    // The roast follows how the phone is held now, not only what raised the level: the neck while
    // hunched, the time in the app while upright; before any reading, the level's own reason.
    private fun roastTopicFor(levelReason: TriggerReason): TriggerReason =
        when (isHunched.value) {
            true -> TriggerReason.POSTURE
            false -> TriggerReason.USAGE_TIME
            null -> levelReason
        }

    // A pause that outlived the service (a restart in the middle of it) is not a new request, so it
    // is not counted again nor checked against rule E.
    private suspend fun restorePause() {
        val pausedUntil =
            store.snapshot
                .first()
                .settings.pausedUntilMillis ?: return
        val remainingMs = pausedUntil - wallClock.nowEpochMillis()
        if (remainingMs > 0) coordinator.onPauseRestored(remainingMs)
    }

    private suspend fun tickCoordinator() {
        while (true) {
            delay(EscalationConfig.COORDINATOR_TICK_SEC * MILLIS_PER_SECOND)
            coordinator.onTick()
        }
    }

    private suspend fun rollDayOver() {
        var day = today()
        refreshDay()
        while (true) {
            delay(DayRollover.nextCheckDelayMillis(wallClock.nowEpochMillis(), zone(), EscalationConfig.DAY_CHANGE_CHECK_SEC))
            val now = today()
            if (now != day) {
                day = now
                refreshDay()
            }
        }
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(wallClock.nowEpochMillis()).atZone(zone()).toLocalDate()

    // A failed write (a DataStore IOException) must not end the day watcher; the next read rolls the day over anyway.
    private suspend fun refreshDay() {
        try {
            recorder.refreshDay()
        } catch (e: IOException) {
            log("Day refresh failed: ${e::class.simpleName}")
        }
    }

    private suspend fun logStateChanges() {
        combine(coordinator.likkaState, coordinator.overlayState, StateChangeLog::entryOf)
            .map(StateChangeLog::format)
            .distinctUntilChanged()
            .collect(log)
    }

    private data class RoastSlot(
        val app: TargetApp,
        val level: Int,
        val reason: TriggerReason,
    )

    private data class ShownRoast(
        val slot: RoastSlot,
        val text: String,
    )

    private companion object {
        const val NO_LEVEL = 0
        const val FIRST_LEVEL = 1
        const val LAST_LEVEL = 3
        const val MILLIS_PER_SECOND = 1_000L
    }
}
