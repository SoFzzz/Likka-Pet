package com.likkapet.data.sensor

import com.likkapet.domain.EscalationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** RF-P01: the sensor's delivery rate is only a hint; decimation must follow the samples' timestamps. */
class SampleDecimatorTest {
    private val periodNanos = TimeUnit.MILLISECONDS.toNanos(EscalationConfig.POSTURE_SAMPLE_PERIOD_MS.toLong())
    private val jitterNanos = TimeUnit.MILLISECONDS.toNanos(EscalationConfig.POSTURE_SAMPLE_JITTER_MS.toLong())
    private val decimator = SampleDecimator(periodNanos, jitterNanos)

    private fun accepted(timestamps: List<Long>) = timestamps.filter { decimator.accepts(it) }

    private fun List<Long>.gapsMillis() = zipWithNext { a, b -> (b - a) / 1_000_000.0 }

    @Test
    fun `the Redmi 9 rate of 48 Hz comes out at 5 Hz`() {
        val kept = accepted(timestampsAt(hz = 48, seconds = 10))

        assertTrue("kept ${kept.size} of 480", kept.size in 49..51)
    }

    @Test
    fun `at 48 Hz no gap strays far from the 200 ms period`() {
        val gaps = accepted(timestampsAt(hz = 48, seconds = 10)).gapsMillis()

        // One 48 Hz step is 20.8 ms: gaps are 187.5 or 208.3 ms, never a burst or a hole.
        assertTrue("gaps $gaps", gaps.all { it in 180.0..215.0 })
    }

    @Test
    fun `the emulator rate of 20 Hz keeps every fourth sample`() {
        val kept = accepted(timestampsAt(hz = 20, seconds = 10))

        assertEquals(50, kept.size)
    }

    @Test
    fun `a sensor that really delivers 5 Hz with jitter loses nothing`() {
        val jittered = listOf(0L, 197, 405, 596, 803, 1_001, 1_198).map { it * 1_000_000L }

        assertEquals(jittered, accepted(jittered))
    }

    @Test
    fun `a sensor slower than the period passes every sample`() {
        val slow = timestampsAt(hz = 2, seconds = 6)

        assertEquals(slow, accepted(slow))
    }

    @Test
    fun `after a long silence it starts a new grid instead of firing a burst`() {
        accepted(timestampsAt(hz = 48, seconds = 1))
        val afterGap = timestampsAt(hz = 48, seconds = 1).map { it + 60 * NANOS_PER_SECOND }

        val kept = accepted(afterGap)

        assertTrue("kept ${kept.size}", kept.size in 4..6)
        assertEquals(afterGap.first(), kept.first())
    }
}
