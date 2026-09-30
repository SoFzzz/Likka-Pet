package com.likkapet.domain.model

/**
 * What the overlay window shows (documentación §6 Módulo 3), derived from the coordinator's state
 * and the planner's motion by `OverlaySceneReducer`. The service only applies it to the window.
 */
sealed interface OverlayScene {
    /** No window: nothing to show (RF-O04 after the farewell, or no level at all). */
    data object Withdrawn : OverlayScene

    /**
     * The window stays, invisible and untouchable, with its state kept: a call or the screen off
     * (`SUSPENDED`, RF-O06) or the watched app left in Level 1–2 (RF-O07).
     */
    data object Hidden : OverlayScene

    /**
     * Ejected (RF-O03): the window is removed. [sendsHome] when the watched app is still in front
     * ("Me rindo" or the end of the countdown): the launcher is opened first. When the user already
     * left for another app, nothing is opened over it.
     */
    data class Ejected(
        val sendsHome: Boolean,
    ) : OverlayScene

    /** Levels 1–2: Likka with its level halo and the roast bubble (RF-O02). [motion] is null until placed. */
    data class Companion(
        val level: Int,
        val roast: String?,
        val motion: OverlayMotion?,
    ) : OverlayScene

    /** Level 3 panel with the countdown and "Me rindo" (RF-O02, RF-O03). */
    data class Fury(
        val roast: String?,
        val secondsLeft: Int,
    ) : OverlayScene

    /**
     * Farewell (`goodbye`, RF-O04) or pause (`sit`): Likka standing where it was, drawn at the size of
     * [sizeLevel]. [motion] is null until placed.
     */
    data class Resting(
        val pose: MotionPose,
        val sizeLevel: Int,
        val motion: OverlayMotion?,
    ) : OverlayScene
}
