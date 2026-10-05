package com.likkapet.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.likkapet.data.FakeScreenStateSource
import com.likkapet.data.preferences.DataStoreStatsStore
import com.likkapet.data.preferences.InMemoryPreferencesDataStore
import com.likkapet.data.usage.DailyStatsRecorder
import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.model.AiServiceStatus
import com.likkapet.domain.model.ForegroundState
import com.likkapet.domain.model.LikkaState
import com.likkapet.domain.model.OverlayVisibility
import com.likkapet.domain.model.PauseResult
import com.likkapet.domain.model.PostureReading
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.TriggerReason
import com.likkapet.domain.port.Clock
import com.likkapet.domain.port.ForegroundAppSource
import com.likkapet.domain.port.PostureSource
import com.likkapet.domain.port.RoastGenerator
import com.likkapet.domain.port.StatsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The service's wiring over fakes and a virtual clock: events reach the coordinator, whose state
 * reaches the stats, the pause controller and the log (documentación §3, §9.3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitoringSessionTest {
    private val day = LocalDate.of(2026, 10, 5)
    private val startOfDayMs = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    private class FakeForeground : ForegroundAppSource {
        val polls = MutableSharedFlow<ForegroundState>(extraBufferCapacity = 64)
        override val states: Flow<ForegroundState> = polls
    }

    private class FakePosture : PostureSource {
        val feed = MutableSharedFlow<PostureReading>(extraBufferCapacity = 64)
        override val readings: Flow<PostureReading> = feed
    }

    private class RecordingRoasts : RoastGenerator {
        val prefetches = mutableListOf<Triple<TargetApp, Int, TriggerReason>>()
        val taken = mutableListOf<Triple<TargetApp, Int, TriggerReason>>()
        override val serviceStatus: StateFlow<AiServiceStatus> = MutableStateFlow(AiServiceStatus())

        override fun prefetch(
            app: TargetApp,
            level: Int,
            reason: TriggerReason,
        ) {
            prefetches += Triple(app, level, reason)
        }

        override suspend fun nextRoast(
            app: TargetApp,
            level: Int,
            reason: TriggerReason,
        ): String {
            taken += Triple(app, level, reason)
            return "roast ${taken.size}: ${app}_L${level}_$reason"
        }
    }

    private class CountingStore(
        private val delegate: StatsStore,
    ) : StatsStore by delegate {
        var refreshes = 0
            private set
        var failPauseWrites = false
        var failResumeWrites = false

        override suspend fun resume() {
            if (failResumeWrites) throw IOException("disk full")
            delegate.resume()
        }

        override suspend fun startPause(minutes: Int) {
            if (failPauseWrites) throw IOException("disk full")
            delegate.startPause(minutes)
        }

        override suspend fun refreshDay() {
            refreshes++
            delegate.refreshDay()
        }
    }

    /** One service run over fakes; two rigs on the same [dataStore] model a service restart. */
    private inner class Rig(
        private val testScope: TestScope,
        private val startMs: Long = startOfDayMs + 12 * HOUR_MS,
        dataStore: DataStore<Preferences> = InMemoryPreferencesDataStore(),
    ) {
        private val wallNow: Long get() = startMs + testScope.testScheduler.currentTime
        val store = CountingStore(DataStoreStatsStore(dataStore, { wallNow }) { ZoneOffset.UTC })
        val foreground = FakeForeground()
        val posture = FakePosture()
        val screen = FakeScreenStateSource()
        val roasts = RecordingRoasts()
        val logs = mutableListOf<String>()
        private val clock = Clock { testScope.testScheduler.currentTime }
        val session =
            MonitoringSession(
                scope = testScope.backgroundScope,
                clock = clock,
                wallClock = { wallNow },
                zone = { ZoneOffset.UTC },
                store = store,
                postureSource = posture,
                foregroundAppSource = foreground,
                screenStateSource = screen,
                roastGenerator = roasts,
                recorder = DailyStatsRecorder(store, clock, testScope.backgroundScope, log = { logs += it }),
                log = { logs += it },
            )
        val pauses = SessionPauseController(store).also { it.attach(session) }

        init {
            session.start()
            testScope.runCurrent()
        }

        val overlay get() = session.overlayState.value
        val global get() = session.likkaState.value
    }

    private fun TestScope.advanceSeconds(seconds: Int) {
        advanceTimeBy(seconds * 1_000L + 1)
        runCurrent()
    }

    private fun Rig.poll(
        app: TargetApp? = TargetApp.TIKTOK,
        isInCall: Boolean = false,
    ) = foreground.polls.tryEmit(ForegroundState(app, isInCall))

    private val badPosture = PostureReading(angleDegrees = 30.0, isOnTable = false)
    private val goodPosture = PostureReading(angleDegrees = 80.0, isOnTable = false)
    private val neutralPosture = PostureReading(angleDegrees = 50.0, isOnTable = false)

    private suspend fun StatsStore.today() = snapshot.first().today

    private suspend fun StatsStore.pausedUntil() = snapshot.first().settings.pausedUntilMillis

    private val level3AfterMinutes =
        EscalationConfig.USAGE_THRESHOLD_MIN + EscalationConfig.LEVEL_1_TO_2_MIN + EscalationConfig.LEVEL_2_TO_3_MIN

    @Test
    fun `a watched app open for the usage threshold raises level 1 and the change is logged`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()

            advanceSeconds(EscalationConfig.USAGE_THRESHOLD_MIN * 60)

            assertEquals(1, rig.overlay.level)
            assertEquals(TriggerReason.USAGE_TIME, rig.overlay.reason)
            assertTrue(
                "State: global=WATCHING level=1 reason=USAGE_TIME visibility=SHOWN paused=false farewell=false" in rig.logs,
            )
        }

    @Test
    fun `ten seconds of bad posture in a watched app raise level 1 for POSTURE`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()

            repeat(EscalationConfig.POSTURE_TRIGGER_SEC + 1) {
                rig.posture.feed.tryEmit(badPosture)
                advanceSeconds(1)
            }

            assertEquals(1, rig.overlay.level)
            assertEquals(TriggerReason.POSTURE, rig.overlay.reason)
        }

    @Test
    fun `holding the phone upright no longer sends Likka away`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()
            repeat(EscalationConfig.POSTURE_TRIGGER_SEC + 1) {
                rig.posture.feed.tryEmit(badPosture)
                advanceSeconds(1)
            }

            repeat(EscalationConfig.POSTURE_RESET_SEC * 4) {
                rig.posture.feed.tryEmit(goodPosture)
                advanceSeconds(1)
            }

            assertEquals(1, rig.overlay.level)
            assertEquals(false, rig.overlay.isFarewell)
        }

    @Test
    fun `the roast talks about the neck while hunched and about the app time while upright`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()
            repeat(EscalationConfig.POSTURE_TRIGGER_SEC + 1) {
                rig.posture.feed.tryEmit(badPosture)
                advanceSeconds(1)
            }
            assertEquals("roast 1: TIKTOK_L1_POSTURE", rig.overlay.roast)

            rig.posture.feed.tryEmit(goodPosture)
            advanceSeconds(1)
            assertEquals("roast 2: TIKTOK_L1_USAGE_TIME", rig.overlay.roast)

            // Between 45° and 55° the topic does not flip back and forth.
            rig.posture.feed.tryEmit(neutralPosture)
            advanceSeconds(1)
            assertEquals("roast 2: TIKTOK_L1_USAGE_TIME", rig.overlay.roast)

            rig.posture.feed.tryEmit(badPosture)
            advanceSeconds(1)
            assertEquals("roast 3: TIKTOK_L1_POSTURE", rig.overlay.roast)
        }

    @Test
    fun `bad posture outside a watched app does nothing`() =
        runTest {
            val rig = Rig(this)
            rig.poll(app = null)
            runCurrent()

            repeat(30) {
                rig.posture.feed.tryEmit(badPosture)
                advanceSeconds(1)
            }

            assertEquals(0, rig.overlay.level)
        }

    @Test
    fun `a call freezes the level timers and hides the overlay, and the timers resume afterwards`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()
            advanceSeconds((EscalationConfig.USAGE_THRESHOLD_MIN - 1) * 60)

            rig.poll(isInCall = true)
            runCurrent()
            advanceSeconds(10 * 60)

            assertEquals(LikkaState.SUSPENDED, rig.global)
            assertEquals(OverlayVisibility.HIDDEN_SUSPENDED, rig.overlay.visibility)
            assertEquals(0, rig.overlay.level)

            rig.poll(isInCall = false)
            runCurrent()
            advanceSeconds(61)

            assertEquals(1, rig.overlay.level)
        }

    @Test
    fun `the screen going off suspends and turning it on resumes`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()

            rig.screen.isOn = false
            runCurrent()
            assertEquals(LikkaState.SUSPENDED, rig.global)
            assertEquals(OverlayVisibility.HIDDEN_SUSPENDED, rig.overlay.visibility)

            rig.screen.isOn = true
            runCurrent()
            assertEquals(LikkaState.WATCHING, rig.global)
            assertEquals(OverlayVisibility.SHOWN, rig.overlay.visibility)
        }

    @Test
    fun `the dashboard pause goes through the coordinator and is persisted once`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()

            val result = rig.pauses.requestPause(30)
            runCurrent()

            assertEquals(PauseResult.ACCEPTED, result)
            assertTrue(rig.overlay.isPaused)
            assertEquals(LikkaState.PAUSED, rig.global)
            assertEquals(1, rig.store.today().pauses)
            assertNotNull(rig.store.pausedUntil())
        }

    @Test
    fun `a pause that cannot be written still holds in memory and does not crash`() =
        runTest {
            val rig = Rig(this)
            rig.store.failPauseWrites = true

            val result = rig.pauses.requestPause(30)
            runCurrent()

            assertEquals(PauseResult.ACCEPTED, result)
            assertTrue(rig.overlay.isPaused)
            assertTrue("Pause not persisted: IOException" in rig.logs)
        }

    @Test
    fun `a resume that cannot be written still ends the pause in memory and does not crash`() =
        runTest {
            val rig = Rig(this)
            rig.pauses.requestPause(30)
            rig.store.failResumeWrites = true

            rig.pauses.resume()

            assertFalse(rig.overlay.isPaused)
            assertTrue("Resume not persisted: IOException" in rig.logs)
        }

    @Test
    fun `a pause is refused at level 3 and nothing is counted`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()
            advanceSeconds(level3AfterMinutes * 60)
            assertEquals(3, rig.overlay.level)
            assertTrue(rig.pauses.isLevel3Active.first())

            val result = rig.pauses.requestPause(30)

            assertEquals(PauseResult.REJECTED_LEVEL_3, result)
            assertEquals(0, rig.store.today().pauses)
            assertNull(rig.store.pausedUntil())
        }

    @Test
    fun `the fourth pause of the day is refused by the coordinator`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()

            repeat(EscalationConfig.MAX_PAUSES_PER_DAY) {
                assertEquals(PauseResult.ACCEPTED, rig.pauses.requestPause(15))
                rig.pauses.resume()
            }

            assertEquals(PauseResult.REJECTED_DAILY_LIMIT, rig.pauses.requestPause(15))
            assertEquals(EscalationConfig.MAX_PAUSES_PER_DAY, rig.store.today().pauses)
        }

    @Test
    fun `resuming ends the pause in the coordinator and in the store`() =
        runTest {
            val rig = Rig(this)
            rig.pauses.requestPause(30)

            rig.pauses.resume()

            assertFalse(rig.overlay.isPaused)
            assertNull(rig.store.pausedUntil())
        }

    @Test
    fun `without a session the stored state decides the pause`() =
        runTest {
            val rig = Rig(this)
            rig.pauses.detach(rig.session)

            assertEquals(PauseResult.ACCEPTED, rig.pauses.requestPause(15))
            assertEquals(PauseResult.REJECTED_ALREADY_PAUSED, rig.pauses.requestPause(15))
        }

    @Test
    fun `a pause still running when the service restarts is put back and not counted again`() =
        runTest {
            val dataStore = InMemoryPreferencesDataStore()
            val before = Rig(this, dataStore = dataStore)
            before.pauses.requestPause(30)
            before.session.stop()

            val after = Rig(this, dataStore = dataStore)

            assertTrue(after.overlay.isPaused)
            assertEquals(1, after.store.today().pauses)
        }

    @Test
    fun `an expired pause is not put back`() =
        runTest {
            val dataStore = InMemoryPreferencesDataStore()
            Rig(this, dataStore = dataStore).pauses.requestPause(15)
            advanceSeconds(20 * 60)

            val after = Rig(this, dataStore = dataStore)

            assertFalse(after.overlay.isPaused)
        }

    @Test
    fun `opening a watched app asks for both level 1 roasts once`() =
        runTest {
            val rig = Rig(this)

            rig.poll(TargetApp.YOUTUBE)
            runCurrent()
            rig.poll(TargetApp.YOUTUBE)
            runCurrent()

            assertEquals(
                listOf(
                    Triple(TargetApp.YOUTUBE, 1, TriggerReason.POSTURE),
                    Triple(TargetApp.YOUTUBE, 1, TriggerReason.USAGE_TIME),
                ),
                rig.roasts.prefetches,
            )
        }

    @Test
    fun `an added app prefetches under OTHER and reopening it asks again`() =
        runTest {
            val rig = Rig(this)

            rig.poll(TargetApp.OTHER)
            runCurrent()
            rig.poll(app = null)
            runCurrent()
            rig.poll(TargetApp.OTHER)
            runCurrent()

            assertEquals(4, rig.roasts.prefetches.size)
            assertTrue(rig.roasts.prefetches.all { it.first == TargetApp.OTHER && it.second == 1 })
        }

    @Test
    fun `reaching a level attaches one roast of that key and prefetches the next level`() =
        runTest {
            val rig = Rig(this)
            rig.poll(TargetApp.YOUTUBE)
            runCurrent()

            advanceSeconds(EscalationConfig.USAGE_THRESHOLD_MIN * 60)

            assertEquals("roast 1: YOUTUBE_L1_USAGE_TIME", rig.overlay.roast)
            assertEquals(listOf(Triple(TargetApp.YOUTUBE, 1, TriggerReason.USAGE_TIME)), rig.roasts.taken)
            assertEquals(Triple(TargetApp.YOUTUBE, 2, TriggerReason.USAGE_TIME), rig.roasts.prefetches.last())
        }

    @Test
    fun `the roast stays the same while the level holds, across a call, and changes with the level`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()
            advanceSeconds(EscalationConfig.USAGE_THRESHOLD_MIN * 60)
            val level1Roast = rig.overlay.roast

            rig.poll(isInCall = true)
            advanceSeconds(30)
            rig.poll(isInCall = false)
            advanceSeconds(1)
            assertEquals(level1Roast, rig.overlay.roast)

            advanceSeconds(EscalationConfig.LEVEL_1_TO_2_MIN * 60)
            assertEquals(2, rig.overlay.level)
            assertEquals("roast 2: TIKTOK_L2_USAGE_TIME", rig.overlay.roast)
            assertEquals(2, rig.roasts.taken.size)
        }

    @Test
    fun `level 3 takes its roast and prefetches nothing beyond it`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()

            advanceSeconds(level3AfterMinutes * 60)

            assertEquals(3, rig.overlay.level)
            assertEquals("roast 3: TIKTOK_L3_USAGE_TIME", rig.overlay.roast)
            assertTrue(rig.roasts.prefetches.none { it.second > 3 })
        }

    @Test
    fun `no level means no roast and nothing taken`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()
            advanceSeconds(60)

            assertNull(rig.overlay.roast)
            assertTrue(rig.roasts.taken.isEmpty())
        }

    @Test
    fun `surrendering at level 3 ejects through the coordinator`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()
            advanceSeconds(level3AfterMinutes * 60)

            rig.session.onSurrenderClick()
            runCurrent()

            assertEquals(LikkaState.EJECTED, rig.global)
            assertEquals(0, rig.overlay.level)
            assertEquals(LikkaState.EJECTED, rig.session.overlayFeed.value.likkaState)
            assertEquals(0, rig.session.overlayFeed.value.overlay.level)
        }

    @Test
    fun `the overlay feed carries the roast and the global state together`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()
            advanceSeconds(EscalationConfig.USAGE_THRESHOLD_MIN * 60)

            val feed = rig.session.overlayFeed.value
            assertEquals(LikkaState.WATCHING, feed.likkaState)
            assertEquals(1, feed.overlay.level)
            assertEquals(rig.overlay.roast, feed.overlay.roast)
            assertNotNull(feed.overlay.roast)
        }

    @Test
    fun `a call screen covering the watched app at level 3 suspends instead of ejecting`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()
            advanceSeconds(level3AfterMinutes * 60 + 8)
            assertEquals(12, rig.overlay.level3SecondsLeft)

            // One poll sees the dialer on top (no watched app) and the call at the same time (§4.2).
            rig.poll(app = null, isInCall = true)
            advanceSeconds(30)
            assertEquals(LikkaState.SUSPENDED, rig.global)
            assertEquals(OverlayVisibility.HIDDEN_SUSPENDED, rig.overlay.visibility)

            rig.poll(isInCall = false)
            advanceSeconds(0)
            assertEquals(LikkaState.WATCHING, rig.global)
            assertEquals(3, rig.overlay.level)
            assertEquals(12, rig.overlay.level3SecondsLeft)
        }

    @Test
    fun `surrendering below level 3 does nothing`() =
        runTest {
            val rig = Rig(this)
            rig.poll()
            runCurrent()
            advanceSeconds(EscalationConfig.USAGE_THRESHOLD_MIN * 60)

            rig.session.onSurrenderClick()
            runCurrent()

            assertEquals(LikkaState.WATCHING, rig.global)
            assertEquals(1, rig.overlay.level)
        }

    @Test
    fun `no prefetch outside watched apps`() =
        runTest {
            val rig = Rig(this)

            rig.poll(app = null)
            runCurrent()

            assertTrue(rig.roasts.prefetches.isEmpty())
        }

    @Test
    fun `the foreground log has the mapped app and never a package`() =
        runTest {
            val rig = Rig(this)

            rig.poll(TargetApp.OTHER)
            runCurrent()

            assertTrue("Foreground: app=OTHER inCall=false" in rig.logs)
            assertTrue(rig.logs.none { it.contains("com.") })
        }

    @Test
    fun `stopping the session writes the whole minutes still in memory`() =
        runTest {
            val rig = Rig(this)
            repeat(40) {
                rig.poll()
                advanceSeconds(2)
            }

            rig.session.stop()

            assertEquals(1, rig.store.today().usageMinutes)
        }

    @Test
    fun `the day is refreshed at start and once more when midnight passes`() =
        runTest {
            val rig = Rig(this, startMs = startOfDayMs + 24 * HOUR_MS - 90_000)
            assertEquals(1, rig.store.refreshes)

            advanceSeconds(60)
            assertEquals(1, rig.store.refreshes)

            advanceSeconds(120)
            assertEquals(2, rig.store.refreshes)
            assertEquals(day.plusDays(1), rig.store.today().date)
        }

    @Test
    fun `no extra refresh happens during a day without a day change`() =
        runTest {
            val rig = Rig(this)

            advanceSeconds(10 * 60)

            assertEquals(1, rig.store.refreshes)
        }

    private companion object {
        const val HOUR_MS = 3_600_000L
    }
}
