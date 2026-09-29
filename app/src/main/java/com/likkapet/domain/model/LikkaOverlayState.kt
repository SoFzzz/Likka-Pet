package com.likkapet.domain.model

/** The coordinator's only output towards the UI (documentación §5). */
data class LikkaOverlayState(
    val visibility: OverlayVisibility,
    // max(postureLevel, usageLevel); 0 = nothing to show
    val level: Int,
    // The reason driving `level`; null at level 0
    val reason: TriggerReason?,
    val app: TargetApp?,
    val roast: String?,
    // Level 3 only
    val level3SecondsLeft: Int?,
    // true → render the `sit` pose, no escalation
    val isPaused: Boolean,
    // true → play the goodbye animation (RF-O04) before hiding
    val isFarewell: Boolean,
    // A watched app is the one on screen; on an ejection, only then is the user sent home (RF-O03)
    val isWatchedAppInForeground: Boolean,
) {
    companion object {
        val NOTHING_TO_SHOW =
            LikkaOverlayState(
                visibility = OverlayVisibility.SHOWN,
                level = 0,
                reason = null,
                app = null,
                roast = null,
                level3SecondsLeft = null,
                isPaused = false,
                isFarewell = false,
                isWatchedAppInForeground = false,
            )
    }
}

enum class OverlayVisibility { SHOWN, HIDDEN_WHILE_AWAY, HIDDEN_SUSPENDED }
