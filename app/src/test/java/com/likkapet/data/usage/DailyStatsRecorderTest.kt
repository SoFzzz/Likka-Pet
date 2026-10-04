package com.likkapet.data.usage

import com.likkapet.data.FakeScreenStateSource
import com.likkapet.data.preferences.DataStoreStatsStore
import com.likkapet.data.preferences.InMemoryPreferencesDataStore
import com.likkapet.domain.model.ForegroundState
import com.likkapet.domain.model.LikkaOverlayState
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.port.Clock
import com.likkapet.domain.port.StatsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/** RF-D02 and the stats hooks of the poller (Data 2), over the real store on an in-memory DataStore. */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyStatsRecorderTest {
    private val day =
        LocalDate
            .of(2026, 10, 5)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    private val store = CountingStore(DataStoreStatsStore(InMemoryPreferencesDataStore(), { day }) { ZoneOffset.UTC })

    private val foreground = MutableSharedFlow<ForegroundState>(extraBufferCapacity = 64)
    private val overlay = MutableSharedFlow<LikkaOverlayState>(extraBufferCapacity = 64)
    private val screen = FakeScreenStateSource()

    private val watchedYoutube = ForegroundState(TargetApp.YOUTUBE, isInCall = false)

    private class CountingStore(
        private val delegate: StatsStore,
    ) : StatsStore by delegate {
        var minuteWrites = 0
            private set

        override suspend fun addUsageMinutes(minutes: Int) {
            minuteWrites++
            delegate.addUsageMinutes(minutes)
        }
    }

    private fun TestScope.recorder(): DailyStatsRecorder {
        val clock = Clock { testScheduler.currentTime }
        return DailyStatsRecorder(store, clock, backgroundScope).also {
            it.start(foreground, screen.isScreenOn, overlay)
            runCurrent()
        }
    }

    /** A poll every 2 s with [state] over [seconds] of virtual time, the first one at the start and the last at the end. */
    private fun TestScope.pollFor(
        seconds: Int,
        state: ForegroundState = watchedYoutube,
    ) {
        foreground.tryEmit(state)
        runCurrent()
        repeat(seconds / POLL_SEC) {
            advanceTimeBy(TimeUnit.SECONDS.toMillis(POLL_SEC.toLong()))
            foreground.tryEmit(state)
            runCurrent()
        }
    }

    private suspend fun today() = store.snapshot.first().today

    private fun levelState(level: Int) = LikkaOverlayState.NOTHING_TO_SHOW.copy(level = level)

    @Test
    fun `three minutes in a watched app write at most four times and all three minutes arrive`() =
        runTest {
            val recorder = recorder()

            pollFor(seconds = 180)
            recorder.stop()

            assertTrue("writes=${store.minuteWrites}", store.minuteWrites <= 4)
            assertEquals(3, today().usageMinutes)
        }

    @Test
    fun `nothing is counted while no watched app is on screen`() =
        runTest {
            val recorder = recorder()

            pollFor(seconds = 120, state = ForegroundState(app = null, isInCall = false))
            recorder.stop()

            assertEquals(0, today().usageMinutes)
        }

    @Test
    fun `a call freezes usage time`() =
        runTest {
            val recorder = recorder()

            pollFor(seconds = 120, state = ForegroundState(TargetApp.YOUTUBE, isInCall = true))
            recorder.stop()

            assertEquals(0, today().usageMinutes)
        }

    @Test
    fun `an added app counts like any other`() =
        runTest {
            val recorder = recorder()

            pollFor(seconds = 60, state = ForegroundState(TargetApp.OTHER, isInCall = false))
            pollFor(seconds = 60, state = watchedYoutube)
            recorder.stop()

            assertEquals(2, today().usageMinutes)
        }

    @Test
    fun `stopping waits until every queued write is in the store`() =
        runTest {
            val recorder = recorder()
            pollFor(seconds = 125)

            recorder.stop()

            assertEquals(2, today().usageMinutes)
        }

    @Test
    fun `time with the screen off is not usage time`() =
        runTest {
            val recorder = recorder()
            pollFor(seconds = 10)

            screen.isOn = false
            runCurrent()
            pollFor(seconds = 120)
            recorder.stop()

            assertEquals(0, today().usageMinutes)
        }

    @Test
    fun `a gap between polls is not counted whole`() =
        runTest {
            val recorder = recorder()
            foreground.tryEmit(watchedYoutube)
            runCurrent()

            // The CPU slept for ten minutes between two polls.
            advanceTimeBy(TimeUnit.MINUTES.toMillis(10))
            foreground.tryEmit(watchedYoutube)
            runCurrent()
            recorder.stop()

            assertEquals(0, today().usageMinutes)
        }

    @Test
    fun `an intervention is counted when Likka appears, once per appearance`() =
        runTest {
            val recorder = recorder()

            overlay.tryEmit(levelState(0))
            overlay.tryEmit(levelState(1))
            overlay.tryEmit(levelState(2))
            overlay.tryEmit(levelState(0))
            overlay.tryEmit(levelState(1))
            runCurrent()
            recorder.stop()

            assertEquals(2, today().interventions)
        }

    @Test
    fun `reaching level 3 is counted once`() =
        runTest {
            val recorder = recorder()

            overlay.tryEmit(levelState(1))
            overlay.tryEmit(levelState(2))
            overlay.tryEmit(levelState(3))
            overlay.tryEmit(levelState(3))
            runCurrent()
            recorder.stop()

            assertEquals(1, today().level3Count)
            assertEquals(1, today().interventions)
        }

    @Test
    fun `a level change that is only the overlay reappearing does not add interventions`() =
        runTest {
            val recorder = recorder()

            overlay.tryEmit(levelState(2))
            overlay.tryEmit(levelState(2))
            overlay.tryEmit(levelState(2))
            runCurrent()
            recorder.stop()

            assertEquals(1, today().interventions)
        }

    @Test
    fun `refreshDay reaches the store`() =
        runTest {
            val recorder = recorder()

            recorder.refreshDay()

            assertEquals(LocalDate.of(2026, 10, 5), today().date)
        }

    @Test
    fun `stopping twice is harmless`() =
        runTest {
            val recorder = recorder()
            pollFor(seconds = 60)

            recorder.stop()
            recorder.stop()

            assertEquals(1, today().usageMinutes)
        }

    private companion object {
        const val POLL_SEC = 2
    }
}
