package com.likkapet.domain

import com.likkapet.domain.model.LikkaState
import com.likkapet.domain.model.PauseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A pause that outlives a service restart is put back without counting again (documentación §3.3, RF-S01). */
class PauseRestoreTest {
    private val harness = CoordinatorHarness()
    private val coordinator = harness.coordinator

    @Test
    fun `a restored pause shows as paused and ends when the remaining time is over`() {
        coordinator.onPauseRestored(remainingMs = 90_000)

        assertEquals(LikkaState.PAUSED, harness.state)
        assertTrue(harness.overlay.isPaused)
        harness.advanceBy(seconds = 89)
        assertEquals(LikkaState.PAUSED, harness.state)
        harness.advanceBy(seconds = 2)
        assertEquals(LikkaState.IDLE, harness.state)
    }

    @Test
    fun `restoring ignores rule E, so it works even with the daily limit used up`() {
        coordinator.onPauseRestored(remainingMs = 60_000)

        assertEquals(LikkaState.PAUSED, harness.state)
        assertEquals(PauseResult.REJECTED_ALREADY_PAUSED, coordinator.onPauseSelected(15, pausesUsedToday = 0))
    }

    @Test
    fun `restoring does nothing while a pause is already running`() {
        coordinator.onPauseSelected(15, pausesUsedToday = 0)

        coordinator.onPauseRestored(remainingMs = 1_000)
        harness.advanceBy(seconds = 5)

        assertEquals(LikkaState.PAUSED, harness.state)
    }
}
