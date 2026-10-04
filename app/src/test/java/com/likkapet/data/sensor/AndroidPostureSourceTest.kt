package com.likkapet.data.sensor

import com.likkapet.data.FakeScreenStateSource
import com.likkapet.domain.model.PostureReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RF-P01 and RF-P05 through the whole source: fake 48 Hz events in, 5 Hz readings out, sensors off with the screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidPostureSourceTest {
    private val feed = FakeMotionFeed()
    private val screen = FakeScreenStateSource()

    private fun TestScope.collectReadings(source: AndroidPostureSource = AndroidPostureSource(feed, screen)): MutableList<PostureReading> {
        val readings = mutableListOf<PostureReading>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.readings.collect { readings += it } }
        return readings
    }

    private fun deliverAt48Hz(
        seconds: Int,
        fromNanos: Long = 0,
    ) = timestampsAt(hz = 48, seconds = seconds).forEach { ts ->
        feed.deliver(gravity(fromNanos + ts, UPRIGHT))
        feed.deliver(accelerometer(fromNanos + ts, UPRIGHT))
    }

    @Test
    fun `ten seconds of 48 Hz events reach the coordinator as about 50 readings`() =
        runTest {
            val readings = collectReadings()

            deliverAt48Hz(seconds = 10)

            assertTrue("readings ${readings.size} of 960 raw events", readings.size in 49..51)
        }

    @Test
    fun `events delivered in one burst are cut the same as events spread over time`() =
        runTest {
            val readings = collectReadings()

            // The test clock never advances: only the samples' own timestamps can explain the count.
            deliverAt48Hz(seconds = 10)

            assertEquals(0L, testScheduler.currentTime)
            assertTrue(readings.size in 49..51)
        }

    @Test
    fun `the sensors are registered only while the screen is on`() =
        runTest {
            collectReadings()
            assertEquals(1, feed.activeListeners)

            screen.isOn = false
            assertEquals(0, feed.activeListeners)

            screen.isOn = true
            assertEquals(1, feed.activeListeners)
            assertEquals(2, feed.registrations)
        }

    @Test
    fun `no reading arrives while the screen is off`() =
        runTest {
            val readings = collectReadings()
            screen.isOn = false

            deliverAt48Hz(seconds = 2)

            assertTrue(readings.isEmpty())
        }

    @Test
    fun `turning the screen on starts a fresh table window`() =
        runTest {
            val readings = collectReadings()
            val flat = { ts: Long -> listOf(gravity(ts, FLAT), accelerometer(ts, FLAT)) }
            timestampsAt(48, 4).flatMap(flat).forEach(feed::deliver)
            assertTrue("on the table before the screen went off", readings.last().isOnTable)

            screen.isOn = false
            screen.isOn = true
            readings.clear()
            timestampsAt(48, 1).map { it + 10 * NANOS_PER_SECOND }.flatMap(flat).forEach(feed::deliver)

            // One second of fresh samples is not enough evidence: doubt never freezes POSTURE.
            assertFalse(readings.any { it.isOnTable })
        }

    @Test
    fun `nothing is registered until someone collects the readings`() =
        runTest {
            AndroidPostureSource(feed, screen)

            assertEquals(0, feed.registrations)
        }

    @Test
    fun `a device without gravity falls back to the accelerometer and still emits at 5 Hz`() =
        runTest {
            val accelerometerOnly = FakeMotionFeed(hasGravity = false)
            val readings = collectReadings(AndroidPostureSource(accelerometerOnly, screen))

            timestampsAt(hz = 48, seconds = 10).forEach { accelerometerOnly.deliver(accelerometer(it, UPRIGHT)) }

            assertTrue("readings ${readings.size}", readings.size in 49..51)
            assertTrue(readings.all { it.angleDegrees == 90.0 })
        }
}
