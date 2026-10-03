package com.likkapet.data.usage

import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.UsageMinutesAccumulator
import com.likkapet.domain.model.ForegroundState
import com.likkapet.domain.model.LikkaOverlayState
import com.likkapet.domain.model.ReasonLevel
import com.likkapet.domain.port.Clock
import com.likkapet.domain.port.StatsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Connects the foreground poller and the coordinator's overlay to the daily stats (RF-D01, RF-D02).
 *
 * Usage time adds up in memory on every poll with a watched app on screen, the screen on and no
 * call going on, and reaches the store through [UsageMinutesAccumulator] only every
 * `USAGE_FLUSH_SEC` and when the screen goes off or monitoring stops. A pause does not stop it:
 * the minutes still count for the day (§3.3). An *intervention* is one appearance of Likka (the
 * shown level going from 0 to at least 1), and a level 3 is counted once per reaching it.
 *
 * Every write goes through one channel and one writer coroutine, so [stop] can wait until
 * everything, including the last flushed minutes, is in the store.
 */
class DailyStatsRecorder(
    private val store: StatsStore,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
) {
    // The three collectors may run on different threads of the scope; one lock keeps their state coherent.
    private val lock = Any()
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val accumulator = UsageMinutesAccumulator(clock) { minutes -> enqueue { store.addUsageMinutes(minutes) } }
    private val writer = scope.launch { for (write in writes) runWrite(write) }
    private val collectors = mutableListOf<Job>()
    private var isScreenOn = true
    private var lastPollMs: Long? = null
    private var shownLevel = ReasonLevel.NONE

    /** Starts recording from the three streams; the service gives them its own, [stop] ends them. */
    fun start(
        foreground: Flow<ForegroundState>,
        screenOn: Flow<Boolean>,
        overlay: Flow<LikkaOverlayState>,
    ) {
        collectors += scope.launch { screenOn.distinctUntilChanged().collect(::onScreenChanged) }
        collectors += scope.launch { foreground.collect(::onPoll) }
        collectors += scope.launch { overlay.map { it.level }.distinctUntilChanged().collect(::onShownLevel) }
    }

    /** Stops listening and writes whatever whole minutes are still in memory. Idempotent. */
    suspend fun stop() {
        collectors.forEach { it.cancelAndJoin() }
        collectors.clear()
        accumulator.onStop()
        writes.close()
        writer.join()
    }

    /** Persists the day rollover and the streak (RF-D04); the service calls it when the day changes. */
    suspend fun refreshDay() = store.refreshDay()

    internal fun onScreenChanged(isOn: Boolean) =
        synchronized(lock) {
            isScreenOn = isOn
            if (!isOn) accumulator.onScreenOff()
            lastPollMs = null // The gap while the screen was off is not usage time.
        }

    internal fun onPoll(state: ForegroundState) =
        synchronized(lock) {
            val nowMs = clock.nowMillis()
            val elapsedMs = lastPollMs?.let { minOf(nowMs - it, MAX_POLL_GAP_MS) } ?: 0L
            lastPollMs = nowMs
            if (state.app != null && !state.isInCall && isScreenOn) accumulator.addUsage(elapsedMs)
        }

    internal fun onShownLevel(levelValue: Int) =
        synchronized(lock) {
            val level = ReasonLevel.entries.first { it.value == levelValue }
            if (shownLevel == ReasonLevel.NONE && level != ReasonLevel.NONE) enqueue { store.recordIntervention() }
            if (shownLevel != ReasonLevel.LEVEL_3 && level == ReasonLevel.LEVEL_3) enqueue { store.recordLevel3() }
            shownLevel = level
        }

    // A failed write (e.g. a DataStore IOException) must not kill the writer and lose every later one.
    private suspend fun runWrite(write: suspend () -> Unit) {
        try {
            write()
        } catch (e: IOException) {
            log("Stats write failed: ${e::class.simpleName}")
        }
    }

    private fun enqueue(write: suspend () -> Unit) {
        writes.trySend(write)
    }

    private companion object {
        const val POLLS_PER_GAP_LIMIT = 2L

        // A poll after a long gap (CPU asleep, process frozen) must not count the whole gap as usage.
        val MAX_POLL_GAP_MS = TimeUnit.SECONDS.toMillis(POLLS_PER_GAP_LIMIT * EscalationConfig.FOREGROUND_POLL_SEC)
    }
}
