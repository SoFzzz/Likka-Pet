package com.likkapet.domain

import com.likkapet.domain.model.LocalReaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** RF-O12 and §12.1 "Reacciones": the poke threshold is inclusive in taps and in time. */
class LocalReactionTrackerTest {
    private val clock = FakeClock()
    private val tracker = LocalReactionTracker(clock)

    @Test
    fun `a drag reacts with the drag key`() {
        assertEquals(LocalReaction.DRAG, tracker.onDragStarted())
        assertEquals("drag", LocalReaction.DRAG.key)
    }

    @Test
    fun `two taps do not react`() {
        assertNull(tapAt(0))
        assertNull(tapAt(1_000))
    }

    @Test
    fun `the third tap within the window already pokes`() {
        assertNull(tapAt(0))
        assertNull(tapAt(1_000))
        assertEquals(LocalReaction.POKE, tapAt(2_000))
        assertEquals("poke", LocalReaction.POKE.key)
    }

    @Test
    fun `a third tap exactly at the end of the window still pokes`() {
        tapAt(0)
        tapAt(2_500)
        assertEquals(LocalReaction.POKE, tapAt(WINDOW_MS))
    }

    @Test
    fun `a third tap just after the window does not poke`() {
        tapAt(0)
        tapAt(2_500)
        assertNull(tapAt(WINDOW_MS + 1))
    }

    @Test
    fun `old taps slide out of the window`() {
        tapAt(0)
        tapAt(4_000)
        assertNull(tapAt(6_000))
        assertEquals(LocalReaction.POKE, tapAt(8_000))
    }

    @Test
    fun `after a poke the count starts over`() {
        repeat(EscalationConfig.POKE_REACTION_TAPS) { tapAt(it * 100L) }
        assertNull(tapAt(400))
        assertNull(tapAt(500))
        assertEquals(LocalReaction.POKE, tapAt(600))
    }

    private fun tapAt(millis: Long): LocalReaction? {
        clock.nowMs = millis
        return tracker.onTap()
    }

    private companion object {
        val WINDOW_MS = EscalationConfig.POKE_REACTION_WINDOW_SEC * 1_000L
    }
}
