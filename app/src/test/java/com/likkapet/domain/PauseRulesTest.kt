package com.likkapet.domain

import com.likkapet.domain.model.PauseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Rule E as one pure function, used by the coordinator and the dashboard (RF-S01). */
class PauseRulesTest {
    private val limit = EscalationConfig.MAX_PAUSES_PER_DAY

    private fun rejection(
        isPaused: Boolean = false,
        isEjected: Boolean = false,
        hasTrackAtLevel3: Boolean = false,
        pausesUsedToday: Int = 0,
    ) = PauseRules.rejectionFor(isPaused, isEjected, hasTrackAtLevel3, pausesUsedToday)

    @Test
    fun `a pause within the limit is allowed`() {
        assertNull(rejection(pausesUsedToday = limit - 1))
    }

    @Test
    fun `the fourth pause of the day is rejected`() {
        assertEquals(PauseResult.REJECTED_DAILY_LIMIT, rejection(pausesUsedToday = limit))
    }

    @Test
    fun `level 3 rejects a pause even with pauses left`() {
        assertEquals(PauseResult.REJECTED_LEVEL_3, rejection(hasTrackAtLevel3 = true))
    }

    @Test
    fun `a running pause wins over every other reason`() {
        assertEquals(
            PauseResult.REJECTED_ALREADY_PAUSED,
            rejection(isPaused = true, isEjected = true, hasTrackAtLevel3 = true, pausesUsedToday = limit),
        )
        assertEquals(PauseResult.REJECTED_WHILE_EJECTED, rejection(isEjected = true, hasTrackAtLevel3 = true))
    }

    @Test
    fun `pauses left never go below zero`() {
        assertEquals(limit, PauseRules.pausesLeft(0))
        assertEquals(0, PauseRules.pausesLeft(limit + 2))
    }
}
