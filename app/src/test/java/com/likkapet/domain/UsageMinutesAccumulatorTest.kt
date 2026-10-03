package com.likkapet.domain

import com.likkapet.domain.port.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** RF-D02: minutes add up in memory and are written in batches. */
class UsageMinutesAccumulatorTest {
    private var nowMs = 0L
    private val writes = mutableListOf<Int>()
    private val accumulator = UsageMinutesAccumulator(Clock { nowMs }) { writes += it }

    private fun pollFor(seconds: Int) {
        repeat(seconds / POLL_SEC) {
            nowMs += POLL_MS
            accumulator.addUsage(POLL_MS)
        }
    }

    @Test
    fun `a three minute session writes at most four times`() {
        pollFor(seconds = 180)

        assertTrue("writes=$writes", writes.size <= 4)
        assertEquals(3, writes.sum())
    }

    @Test
    fun `nothing is written before the flush interval`() {
        pollFor(seconds = EscalationConfig.USAGE_FLUSH_SEC - POLL_SEC)

        assertTrue(writes.isEmpty())
    }

    @Test
    fun `turning the screen off flushes the whole minutes and keeps the rest`() {
        pollFor(seconds = 30)
        accumulator.onScreenOff()
        assertTrue(writes.isEmpty())

        pollFor(seconds = 40)
        accumulator.onScreenOff()

        assertEquals(listOf(1), writes)
    }

    @Test
    fun `stopping flushes the whole minutes so they are not lost`() {
        pollFor(seconds = 30)
        accumulator.onStop()
        assertTrue(writes.isEmpty())

        pollFor(seconds = 40)
        accumulator.onStop()

        assertEquals(listOf(1), writes)
    }

    @Test
    fun `seconds left over carry into the next flush`() {
        pollFor(seconds = 90)
        accumulator.onScreenOff()
        pollFor(seconds = 30)
        accumulator.onScreenOff()

        assertEquals(2, writes.sum())
    }

    private companion object {
        const val POLL_SEC = 2
        const val POLL_MS = POLL_SEC * 1_000L
    }
}
