package com.likkapet.domain.model

/**
 * The global state and the overlay state (with its roast) taken together from one publication of
 * the coordinator, so the overlay never pairs an ejection with a stale level, or the other way round.
 */
data class OverlayFeed(
    val likkaState: LikkaState,
    val overlay: LikkaOverlayState,
) {
    companion object {
        val INITIAL = OverlayFeed(LikkaState.IDLE, LikkaOverlayState.NOTHING_TO_SHOW)
    }
}
