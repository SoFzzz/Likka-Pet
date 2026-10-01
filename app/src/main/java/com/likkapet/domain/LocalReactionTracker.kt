package com.likkapet.domain

import com.likkapet.domain.model.LocalReaction
import com.likkapet.domain.port.Clock

/**
 * Local reactions without AI (RF-O12, documentación §9.6), independent of the overlay movement.
 * Picking the phrase from `reactions.json` is the caller's job; this only decides when to react.
 */
class LocalReactionTracker(
    private val clock: Clock,
) {
    private val recentTapsMs = ArrayDeque<Long>()

    /** Every drag reacts once, when it starts. */
    @Synchronized
    fun onDragStarted(): LocalReaction = LocalReaction.DRAG

    /**
     * [LocalReaction.POKE] on the tap that completes [EscalationConfig.POKE_REACTION_TAPS] taps
     * within [EscalationConfig.POKE_REACTION_WINDOW_SEC], both ends inclusive; the count then starts
     * over, so a fourth tap alone does not react again.
     */
    @Synchronized
    fun onTap(): LocalReaction? {
        val nowMs = clock.nowMillis()
        recentTapsMs.addLast(nowMs)
        while (nowMs - recentTapsMs.first() > EscalationTimings.POKE_REACTION_WINDOW_MS) recentTapsMs.removeFirst()
        if (recentTapsMs.size < EscalationConfig.POKE_REACTION_TAPS) return null
        recentTapsMs.clear()
        return LocalReaction.POKE
    }
}
