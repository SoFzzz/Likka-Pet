package com.likkapet.domain

import com.likkapet.domain.model.LocalReaction
import com.likkapet.domain.model.ShownReaction
import com.likkapet.domain.port.Clock
import com.likkapet.domain.port.ReactionPhrases

/**
 * Which local reaction the bubble shows (RF-O12), on top of [LocalReactionTracker]: a drag shows a
 * `drag` phrase while the finger is down and for [EscalationConfig.LOCAL_REACTION_SEC] after the
 * drop; a poke shows a `poke` phrase for that long. A newer reaction replaces the one on screen.
 * Synchronous like the planner: the caller asks [current] again after [msUntilExpiry].
 */
class LocalReactionDisplay(
    private val clock: Clock,
    private val tracker: LocalReactionTracker,
    private val phrases: ReactionPhrases,
) {
    private var shown: ShownReaction? = null
    private var expiresAtMs = 0L
    private var isDragging = false

    @Synchronized
    fun onDragStarted() {
        isDragging = true
        show(tracker.onDragStarted())
    }

    @Synchronized
    fun onDragEnded() {
        if (!isDragging) return
        isDragging = false
        if (shown?.reaction == LocalReaction.DRAG) expiresAtMs = clock.nowMillis() + EscalationTimings.LOCAL_REACTION_MS
    }

    /** A tap on Likka; the third one within the window shows a poke (RF-O12). */
    @Synchronized
    fun onTap() {
        tracker.onTap()?.let(::show)
    }

    /** The reaction to show now, or null. */
    @Synchronized
    fun current(): ShownReaction? {
        val reaction = shown ?: return null
        if (isDragging && reaction.reaction == LocalReaction.DRAG) return reaction
        if (clock.nowMillis() < expiresAtMs) return reaction
        shown = null
        return null
    }

    /** How long until [current] changes on its own; null when nothing is shown or a drag holds it. */
    @Synchronized
    fun msUntilExpiry(): Long? {
        val reaction = current() ?: return null
        if (isDragging && reaction.reaction == LocalReaction.DRAG) return null
        return expiresAtMs - clock.nowMillis()
    }

    private fun show(reaction: LocalReaction) {
        shown = ShownReaction(reaction, phrases.next(reaction))
        expiresAtMs = clock.nowMillis() + EscalationTimings.LOCAL_REACTION_MS
    }
}
