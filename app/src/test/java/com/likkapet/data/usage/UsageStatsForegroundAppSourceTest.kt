package com.likkapet.data.usage

import com.likkapet.data.FakeScreenStateSource
import com.likkapet.domain.model.ForegroundState
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.port.WallClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/** RF-A01 (poller), RF-A03 (calls) and RF-S06 (added apps map to OTHER), in virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class UsageStatsForegroundAppSourceTest {
    private val youtube = "com.google.android.youtube"
    private val addedChat = "org.example.chat"
    private val launcher = "com.miui.home"

    private var foregroundPackage: String? = null
    private var isInCall = false
    private val reads = mutableListOf<Long>()
    private val screen = FakeScreenStateSource()
    private val watched = MutableStateFlow(setOf(youtube, addedChat))

    private fun TestScope.source(): UsageStatsForegroundAppSource {
        val wallClock = WallClock { testScheduler.currentTime }
        val reader =
            ResumedEventsReader { _, to ->
                reads += to
                listOfNotNull(foregroundPackage?.let { ResumedEvent(it, to) })
            }
        return UsageStatsForegroundAppSource(
            tracker = ForegroundPackageTracker(reader, wallClock),
            callStateReader = CallStateReader { isInCall },
            screenState = screen,
            watchedPackages = watched,
            pollDispatcher = StandardTestDispatcher(testScheduler),
        )
    }

    private fun TestScope.collect(source: UsageStatsForegroundAppSource): List<ForegroundState> {
        val states = mutableListOf<ForegroundState>()
        backgroundScope.launch { source.states.collect { states += it } }
        runCurrent()
        return states
    }

    @Test
    fun `a default app is mapped to its TargetApp`() =
        runTest {
            foregroundPackage = youtube

            val states = collect(source())

            assertEquals(ForegroundState(TargetApp.YOUTUBE, isInCall = false), states.last())
        }

    @Test
    fun `an app the user added is OTHER`() =
        runTest {
            foregroundPackage = addedChat

            val states = collect(source())

            assertEquals(TargetApp.OTHER, states.last().app)
        }

    @Test
    fun `an app that is not watched is null`() =
        runTest {
            foregroundPackage = launcher

            val states = collect(source())

            assertEquals(null, states.last().app)
        }

    @Test
    fun `polls every 2 seconds`() =
        runTest {
            foregroundPackage = youtube
            collect(source())
            assertEquals(1, reads.size)

            advanceTimeBy(TimeUnit.SECONDS.toMillis(1))
            runCurrent()
            assertEquals(1, reads.size)

            advanceTimeBy(TimeUnit.SECONDS.toMillis(1))
            runCurrent()
            assertEquals(2, reads.size)

            advanceTimeBy(TimeUnit.SECONDS.toMillis(6))
            runCurrent()
            assertEquals(5, reads.size)
        }

    @Test
    fun `it emits on every poll even when nothing changed`() =
        runTest {
            foregroundPackage = youtube
            val states = collect(source())

            advanceTimeBy(TimeUnit.SECONDS.toMillis(6))
            runCurrent()

            assertEquals(4, states.size)
        }

    @Test
    fun `switching to another app shows up within one poll`() =
        runTest {
            foregroundPackage = youtube
            val states = collect(source())

            foregroundPackage = launcher
            advanceTimeBy(TimeUnit.SECONDS.toMillis(2))
            runCurrent()

            assertEquals(null, states.last().app)
        }

    @Test
    fun `a call is reported within the next poll and ends with it`() =
        runTest {
            foregroundPackage = youtube
            val states = collect(source())
            assertEquals(false, states.last().isInCall)

            isInCall = true
            advanceTimeBy(TimeUnit.SECONDS.toMillis(2))
            runCurrent()
            assertEquals(true, states.last().isInCall)

            isInCall = false
            advanceTimeBy(TimeUnit.SECONDS.toMillis(2))
            runCurrent()
            assertEquals(false, states.last().isInCall)
        }

    @Test
    fun `a known dialer in the foreground counts as a call`() =
        runTest {
            foregroundPackage = "com.google.android.dialer"

            val states = collect(source())

            assertEquals(true, states.last().isInCall)
        }

    @Test
    fun `after hanging up, a dialer still in front keeps reporting a call until the watched app is back`() =
        runTest {
            foregroundPackage = "com.android.incallui"
            isInCall = true
            val states = collect(source())

            // AudioManager already says the call is over, but the call screen is still in front.
            isInCall = false
            advanceTimeBy(TimeUnit.SECONDS.toMillis(2))
            runCurrent()
            assertEquals(ForegroundState(app = null, isInCall = true), states.last())

            foregroundPackage = youtube
            advanceTimeBy(TimeUnit.SECONDS.toMillis(2))
            runCurrent()
            assertEquals(ForegroundState(app = TargetApp.YOUTUBE, isInCall = false), states.last())
        }

    @Test
    fun `after a VoIP call, the app that carried it in front is reported as no watched app and no call`() =
        runTest {
            foregroundPackage = "com.whatsapp"
            isInCall = true
            val states = collect(source())
            assertEquals(ForegroundState(app = null, isInCall = true), states.last())

            isInCall = false
            advanceTimeBy(TimeUnit.SECONDS.toMillis(2))
            runCurrent()
            assertEquals(ForegroundState(app = null, isInCall = false), states.last())
        }

    @Test
    fun `switching a watched app off takes effect at once, without waiting for the next poll`() =
        runTest {
            foregroundPackage = youtube
            val states = collect(source())
            assertEquals(TargetApp.YOUTUBE, states.last().app)

            watched.value = setOf(addedChat)
            runCurrent()

            assertEquals(null, states.last().app)
        }

    @Test
    fun `with the screen off the usage stats are not queried but a call is still reported`() =
        runTest {
            foregroundPackage = youtube
            val states = collect(source())
            val readsBefore = reads.size

            screen.isOn = false
            isInCall = true
            advanceTimeBy(TimeUnit.SECONDS.toMillis(6))
            runCurrent()

            assertEquals(readsBefore, reads.size)
            assertEquals(ForegroundState(TargetApp.YOUTUBE, isInCall = true), states.last())
        }

    @Test
    fun `turning the screen on polls again immediately`() =
        runTest {
            foregroundPackage = youtube
            collect(source())
            screen.isOn = false
            runCurrent()
            val readsBefore = reads.size

            foregroundPackage = launcher
            screen.isOn = true
            runCurrent()

            assertEquals(readsBefore + 1, reads.size)
        }
}
