package com.likkapet.service

import com.likkapet.domain.PauseRules
import com.likkapet.domain.model.LikkaOverlayState
import com.likkapet.domain.model.LikkaSnapshot
import com.likkapet.domain.model.LikkaState
import com.likkapet.domain.model.ReasonLevel

/** The three texts of the persistent notification (RF-O08, RF-A06). */
enum class GuardianMode { PROTECTING, PAUSED, NEEDS_PERMISSION }

/** Everything the notification shows; the service rebuilds it only when this changes. */
data class GuardianNotificationState(
    val mode: GuardianMode,
    // The "Pausar" action: hidden at Level 3, while ejected or paused, or with no pauses left (RF-O08).
    val canPause: Boolean,
) {
    companion object {
        /** Before the first real state is known: no pause action, so it never offers one it cannot honour. */
        val INITIAL = GuardianNotificationState(GuardianMode.PROTECTING, canPause = false)

        fun of(
            likkaState: LikkaState,
            overlay: LikkaOverlayState,
            snapshot: LikkaSnapshot,
            hasAllPermissions: Boolean,
        ): GuardianNotificationState {
            val canPause =
                hasAllPermissions &&
                    PauseRules.rejectionFor(
                        isPaused = likkaState == LikkaState.PAUSED,
                        isEjected = likkaState == LikkaState.EJECTED,
                        hasTrackAtLevel3 = overlay.level == ReasonLevel.LEVEL_3.value,
                        pausesUsedToday = snapshot.today.pauses,
                    ) == null
            val mode =
                when {
                    !hasAllPermissions -> GuardianMode.NEEDS_PERMISSION
                    likkaState == LikkaState.PAUSED -> GuardianMode.PAUSED
                    else -> GuardianMode.PROTECTING
                }
            return GuardianNotificationState(mode, canPause)
        }
    }
}
