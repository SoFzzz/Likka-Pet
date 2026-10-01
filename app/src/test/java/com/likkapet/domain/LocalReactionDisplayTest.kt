package com.likkapet.domain

import com.likkapet.domain.model.LocalReaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** RF-O12: which local reaction the bubble shows and for how long; phrases come from a fake, never the network. */
class LocalReactionDisplayTest {
    private val clock = FakeClock()
    private val asked = mutableListOf<LocalReaction>()
    private val display =
        LocalReactionDisplay(clock, LocalReactionTracker(clock)) { reaction ->
            asked += reaction
            "${reaction.key} #${asked.size}"
        }
    private val reactionMs = EscalationConfig.LOCAL_REACTION_SEC * 1_000L

    @Test
    fun `nothing is shown before any gesture`() {
        assertNull(display.current())
        assertNull(display.msUntilExpiry())
    }

    @Test
    fun `a drag shows a drag phrase right away`() {
        display.onDragStarted()

        assertEquals(LocalReaction.DRAG, display.current()?.reaction)
        assertEquals("drag #1", display.current()?.text)
    }

    @Test
    fun `the drag phrase stays while the finger is down, however long`() {
        display.onDragStarted()
        clock.nowMs += 3 * reactionMs

        assertEquals(LocalReaction.DRAG, display.current()?.reaction)
        assertNull(display.msUntilExpiry())
    }

    @Test
    fun `the drag phrase goes LOCAL_REACTION_SEC after the drop`() {
        display.onDragStarted()
        clock.nowMs += 10_000
        display.onDragEnded()

        assertEquals(reactionMs, display.msUntilExpiry())
        clock.nowMs += reactionMs - 1
        assertEquals(LocalReaction.DRAG, display.current()?.reaction)
        clock.nowMs += 1
        assertNull(display.current())
    }

    @Test
    fun `two taps show nothing`() {
        tapAt(0)
        tapAt(1_000)

        assertNull(display.current())
        assertEquals(emptyList<LocalReaction>(), asked)
    }

    @Test
    fun `the third tap within the window already shows a poke phrase`() {
        tapAt(0)
        tapAt(1_000)
        tapAt(2_000)

        assertEquals(LocalReaction.POKE, display.current()?.reaction)
        assertEquals(listOf(LocalReaction.POKE), asked)
    }

    @Test
    fun `a poke lasts LOCAL_REACTION_SEC`() {
        repeat(EscalationConfig.POKE_REACTION_TAPS) { tapAt(0) }

        clock.nowMs = reactionMs - 1
        assertEquals(LocalReaction.POKE, display.current()?.reaction)
        clock.nowMs = reactionMs
        assertNull(display.current())
    }

    @Test
    fun `a fourth tap right after a poke does not poke again`() {
        repeat(EscalationConfig.POKE_REACTION_TAPS + 1) { tapAt(0) }

        assertEquals(listOf(LocalReaction.POKE), asked)
    }

    @Test
    fun `a drag replaces a poke on screen`() {
        repeat(EscalationConfig.POKE_REACTION_TAPS) { tapAt(0) }
        display.onDragStarted()

        assertEquals(LocalReaction.DRAG, display.current()?.reaction)
    }

    @Test
    fun `every drag takes a new phrase`() {
        display.onDragStarted()
        display.onDragEnded()
        display.onDragStarted()

        assertEquals("drag #2", display.current()?.text)
    }

    @Test
    fun `a drop without a drag changes nothing`() {
        display.onDragEnded()

        assertNull(display.current())
    }

    private fun tapAt(ms: Long) {
        clock.nowMs = ms
        display.onTap()
    }
}
