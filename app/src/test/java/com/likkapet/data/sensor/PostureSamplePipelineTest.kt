package com.likkapet.data.sensor

import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.model.PostureReading
import com.likkapet.domain.model.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.sin

/** RF-P01–RF-P03 over a fake event sequence at the rate the Redmi 9 really delivers (~48 Hz). */
class PostureSamplePipelineTest {
    private val rate = 48

    private fun run(
        pipeline: PostureSamplePipeline,
        seconds: Int,
        gravityVector: Vector3 = FLAT,
        accelerometerVector: (Int) -> Vector3 = { FLAT },
        fromNanos: Long = 0,
    ): List<Pair<Long, PostureReading>> =
        timestampsAt(rate, seconds).flatMapIndexed { index, ts ->
            val at = ts + fromNanos
            listOfNotNull(
                pipeline.onSample(gravity(at, gravityVector)),
                pipeline.onSample(accelerometer(at, accelerometerVector(index))),
            ).map { at to it }
        }

    @Test
    fun `48 Hz gravity events become about 5 readings per second`() {
        val readings = run(PostureSamplePipeline(hasGravity = true), seconds = 10)

        assertTrue("readings ${readings.size}", readings.size in 49..51)
    }

    @Test
    fun `without gravity, 48 Hz accelerometer events also become about 5 readings per second`() {
        val pipeline = PostureSamplePipeline(hasGravity = false)
        val readings = timestampsAt(rate, 10).mapNotNull { pipeline.onSample(accelerometer(it, FLAT)) }

        assertTrue("readings ${readings.size}", readings.size in 49..51)
    }

    @Test
    fun `the angle comes from the gravity vector`() {
        val readings = run(PostureSamplePipeline(hasGravity = true), seconds = 1, gravityVector = UPRIGHT)

        assertTrue(readings.all { (_, reading) -> reading.angleDegrees == 90.0 })
    }

    @Test
    fun `a flat phone is only on the table once the 2 s window of 5 Hz samples is full`() {
        val readings = run(PostureSamplePipeline(hasGravity = true), seconds = 4)

        // Undecimated, 10 samples would be there after 0.2 s; at 5 Hz they take about 1.9 s.
        val early = readings.filter { (ts, _) -> ts < 1_500_000_000L }
        val late = readings.filter { (ts, _) -> ts > 2_200_000_000L }
        assertTrue(early.none { (_, reading) -> reading.isOnTable })
        assertTrue(late.isNotEmpty() && late.all { (_, reading) -> reading.isOnTable })
    }

    @Test
    fun `a flat phone with hand tremor is not on the table`() {
        // |a| alternates by ±0.15 m/s², the "on the lap" case of scenario M3.
        val tremor = { index: Int -> Vector3(0.0, 0.0, 9.81 + if (index % 2 == 0) 0.15 else -0.15) }
        val readings = run(PostureSamplePipeline(hasGravity = true), seconds = 6, accelerometerVector = tremor)

        assertFalse(readings.any { (_, reading) -> reading.isOnTable })
    }

    @Test
    fun `table detection uses the raw accelerometer even though the angle comes from gravity`() {
        // Perfectly still gravity but a shaking accelerometer: gravity alone could never show the tremor.
        val shaking = { index: Int -> Vector3(0.0, 0.0, 9.81 + sin(index.toDouble()) * 0.3) }
        val readings = run(PostureSamplePipeline(hasGravity = true), seconds = 4, accelerometerVector = shaking)

        assertFalse(readings.any { (_, reading) -> reading.isOnTable })
    }

    @Test
    fun `an upright phone is never on the table however still it is`() {
        val readings = run(PostureSamplePipeline(hasGravity = true), seconds = 4, gravityVector = UPRIGHT)

        assertFalse(readings.any { (_, reading) -> reading.isOnTable })
    }

    @Test
    fun `the EMA fallback starts at the first sample and moves 15 percent of the way per 5 Hz sample`() {
        val pipeline = PostureSamplePipeline(hasGravity = false)
        val alpha = EscalationConfig.ACCELEROMETER_EMA_ALPHA
        val flatSeconds = run(pipeline, seconds = 2, accelerometerVector = { FLAT })
        val firstAfterFlat = flatSeconds.first().second.angleDegrees
        assertEquals(0.0, firstAfterFlat, 1e-9)

        val upright = timestampsAt(rate, 1).map { it + 2 * NANOS_PER_SECOND }.mapNotNull { pipeline.onSample(accelerometer(it, UPRIGHT)) }

        val step1 = Math.toDegrees(atan2(alpha, 1 - alpha))
        val step2 = Math.toDegrees(atan2(1 - (1 - alpha) * (1 - alpha), (1 - alpha) * (1 - alpha)))
        assertEquals(step1, upright[0].angleDegrees, 0.01)
        assertEquals(step2, upright[1].angleDegrees, 0.01)
    }

    @Test
    fun `the EMA is applied after decimation, not to every raw event`() {
        val pipeline = PostureSamplePipeline(hasGravity = false)
        run(pipeline, seconds = 1, accelerometerVector = { FLAT })
        val upright = timestampsAt(rate, 1).map { it + 2 * NANOS_PER_SECOND }.mapNotNull { pipeline.onSample(accelerometer(it, UPRIGHT)) }

        // Filtering all 48 raw samples a second would have converged on 90° almost at once.
        assertTrue("after one second ${upright.last().angleDegrees}", upright.last().angleDegrees < 70.0)
    }

    @Test
    fun `the EMA fallback still detects the table from the raw magnitudes`() {
        val pipeline = PostureSamplePipeline(hasGravity = false)
        val readings = timestampsAt(rate, 4).mapNotNull { pipeline.onSample(accelerometer(it, FLAT)) }

        assertTrue(readings.last().isOnTable)
    }

    @Test
    fun `gravity events are ignored when the device has no gravity sensor`() {
        val pipeline = PostureSamplePipeline(hasGravity = false)

        assertNull(pipeline.onSample(gravity(0, UPRIGHT)))
    }

    @Test
    fun `a sample without direction or with non-finite values is dropped`() {
        val pipeline = PostureSamplePipeline(hasGravity = true)

        assertNull(pipeline.onSample(gravity(0, Vector3(0.0, 0.0, 0.0))))
        assertNull(pipeline.onSample(gravity(NANOS_PER_SECOND, Vector3(Double.NaN, 0.0, 9.81))))
        assertNull(pipeline.onSample(accelerometer(2 * NANOS_PER_SECOND, Vector3(Double.POSITIVE_INFINITY, 0.0, 0.0))))
    }
}
