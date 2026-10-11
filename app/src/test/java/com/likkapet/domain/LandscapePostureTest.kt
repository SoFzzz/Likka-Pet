package com.likkapet.domain

import com.likkapet.domain.model.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner decision 2026-10-11: landscape has its own posture thresholds (documentación §4). */
class LandscapePostureTest {
    @Test
    fun `gravity along the long axis is portrait, along the short axis is landscape`() {
        assertFalse(reading(Vector3(0.0, 8.0, 5.0)).isLandscape)
        assertTrue(reading(Vector3(8.0, 0.0, 5.0)).isLandscape)
    }

    @Test
    fun `64 degrees is bad posture in landscape but healthy in portrait`() {
        assertEquals(PostureZone.BAD, PostureZone.of(64.0, isLandscape = true))
        assertEquals(PostureZone.GOOD, PostureZone.of(64.0, isLandscape = false))
    }

    @Test
    fun `landscape has a 10 degree hysteresis band`() {
        assertEquals(PostureZone.NEUTRAL, PostureZone.of(75.0, isLandscape = true))
        assertEquals(PostureZone.GOOD, PostureZone.of(81.0, isLandscape = true))
    }

    private fun reading(orientation: Vector3) = checkNotNull(BiomechanicsCalculator.postureReading(orientation, emptyList()))
}
