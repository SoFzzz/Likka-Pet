package com.likkapet.domain

import com.likkapet.domain.model.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** documentación §4 and §12.1 "BiomechanicsCalculator" (RF-P02). */
class BiomechanicsCalculatorTest {
    @Test
    fun `upright phone in front of the face is 90 degrees`() {
        assertEquals(90.0, tilt(Vector3(0.0, G, 0.0)), DELTA)
    }

    @Test
    fun `flat phone face up is 0 degrees`() {
        assertEquals(0.0, tilt(Vector3(0.0, 0.0, G)), DELTA)
    }

    @Test
    fun `phone above the face while lying down is past 90 degrees`() {
        assertEquals(180.0, tilt(Vector3(0.0, 0.0, -G)), DELTA)
    }

    @Test
    fun `tilt ignores the length of the vector`() {
        assertEquals(45.0, tilt(Vector3(0.0, 3.0, 3.0)), DELTA)
        assertEquals(45.0, tilt(Vector3(0.0, 300.0, 300.0)), DELTA)
    }

    @Test
    fun `components whose rounded cosine exceeds 1 give 0 degrees, never NaN`() {
        // Subnormal squares lose precision: here z / |v| rounds to 1.0000000000000866.
        val vector = Vector3(-7.312715117751976e-169, 0.0, 3.295621231654795e-156)
        assertEquals(0.0, tilt(vector), 0.0)
    }

    @Test
    fun `cosine outside -1 to 1 is clamped before acos`() {
        assertEquals(0.0, BiomechanicsCalculator.angleFromCosine(1.0000000000000002), 0.0)
        assertEquals(180.0, BiomechanicsCalculator.angleFromCosine(-1.0000000000000002), DELTA)
    }

    @Test
    fun `zero vector has no angle and is dropped`() {
        assertNull(BiomechanicsCalculator.screenTiltDegrees(Vector3(0.0, 0.0, 0.0)))
    }

    @Test
    fun `non finite vector has no angle and is dropped`() {
        assertNull(BiomechanicsCalculator.screenTiltDegrees(Vector3(Double.NaN, 0.0, G)))
        assertNull(BiomechanicsCalculator.screenTiltDegrees(Vector3(Double.POSITIVE_INFINITY, 0.0, G)))
    }

    @Test
    fun `magnitude is the euclidean length`() {
        assertEquals(13.0, BiomechanicsCalculator.magnitude(Vector3(3.0, 4.0, 12.0)), DELTA)
    }

    @Test
    fun `standard deviation of constant samples is 0`() {
        assertEquals(0.0, BiomechanicsCalculator.standardDeviation(List(10) { G }), 0.0)
    }

    @Test
    fun `standard deviation is the population one`() {
        val values = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)
        assertEquals(2.0, BiomechanicsCalculator.standardDeviation(values), DELTA)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `standard deviation of no samples is rejected`() {
        BiomechanicsCalculator.standardDeviation(emptyList())
    }

    @Test
    fun `posture reading carries the angle`() {
        val reading = BiomechanicsCalculator.postureReading(Vector3(0.0, G, 0.0), List(10) { G })
        assertNotNull(reading)
        assertEquals(90.0, checkNotNull(reading).angleDegrees, DELTA)
    }

    @Test
    fun `posture reading of a zero vector is dropped`() {
        assertNull(BiomechanicsCalculator.postureReading(Vector3(0.0, 0.0, 0.0), List(10) { G }))
    }

    private fun tilt(vector: Vector3): Double = checkNotNull(BiomechanicsCalculator.screenTiltDegrees(vector))

    private companion object {
        const val G = 9.81
        const val DELTA = 1e-9
    }
}
