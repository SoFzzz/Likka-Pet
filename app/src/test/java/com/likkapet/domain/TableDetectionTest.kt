package com.likkapet.domain

import com.likkapet.domain.model.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** documentación §4.1 triple condition, RF-P03 and §12.1 "Detección de mesa". */
class TableDetectionTest {
    @Test
    fun `flat and still is on the table`() {
        assertTrue(BiomechanicsCalculator.isOnTable(FLAT, still()))
    }

    @Test
    fun `flat with hand tremor is not on the table (phone on the lap)`() {
        val tremor = shaky(amplitude = 0.15)
        assertEquals(0.15, BiomechanicsCalculator.standardDeviation(tremor), 1e-9)
        assertFalse(BiomechanicsCalculator.isOnTable(FLAT, tremor))
    }

    @Test
    fun `tilted and still is not on the table`() {
        assertFalse(BiomechanicsCalculator.isOnTable(Vector3(0.0, 6.9, 6.9), still()))
    }

    @Test
    fun `stddev just under the limit is still, just over it is not`() {
        assertTrue(BiomechanicsCalculator.isOnTable(FLAT, shaky(amplitude = 0.049)))
        assertFalse(BiomechanicsCalculator.isOnTable(FLAT, shaky(amplitude = 0.051)))
    }

    @Test
    fun `z exactly at the minimum is not flat`() {
        assertFalse(BiomechanicsCalculator.isOnTable(Vector3(0.0, 0.0, EscalationConfig.TABLE_MIN_Z), still()))
    }

    @Test
    fun `abs y exactly at the maximum is not flat, on either side`() {
        val limit = EscalationConfig.TABLE_MAX_ABS_Y
        assertFalse(BiomechanicsCalculator.isOnTable(Vector3(0.0, limit, 9.5), still()))
        assertFalse(BiomechanicsCalculator.isOnTable(Vector3(0.0, -limit, 9.5), still()))
    }

    @Test
    fun `small negative y still counts as flat`() {
        assertTrue(BiomechanicsCalculator.isOnTable(Vector3(0.0, -1.9, 9.5), still()))
    }

    @Test
    fun `face down is not on the table`() {
        assertFalse(BiomechanicsCalculator.isOnTable(Vector3(0.0, 0.0, -G), still()))
    }

    @Test
    fun `an incomplete window is not on the table`() {
        val window = still().drop(1)
        assertFalse(BiomechanicsCalculator.isOnTable(FLAT, window))
    }

    @Test
    fun `only the last window of samples counts`() {
        val window = shaky(amplitude = 1.0) + still()
        assertTrue(BiomechanicsCalculator.isOnTable(FLAT, window))
        assertFalse(BiomechanicsCalculator.isOnTable(FLAT, still() + shaky(amplitude = 1.0)))
    }

    @Test
    fun `posture reading reports the table classification`() {
        assertTrue(checkNotNull(BiomechanicsCalculator.postureReading(FLAT, still())).isOnTable)
        assertFalse(checkNotNull(BiomechanicsCalculator.postureReading(FLAT, shaky(amplitude = 0.15))).isOnTable)
    }

    private fun still(): List<Double> = List(EscalationConfig.TABLE_WINDOW_SAMPLES) { G }

    // Alternating ±amplitude around g: its population standard deviation is exactly the amplitude.
    private fun shaky(amplitude: Double): List<Double> =
        List(EscalationConfig.TABLE_WINDOW_SAMPLES) { if (it % 2 == 0) G + amplitude else G - amplitude }

    private companion object {
        const val G = 9.81
        val FLAT = Vector3(0.1, 0.2, G)
    }
}
