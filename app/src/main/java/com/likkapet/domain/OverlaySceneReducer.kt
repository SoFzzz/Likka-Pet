package com.likkapet.domain

import com.likkapet.domain.model.LikkaOverlayState
import com.likkapet.domain.model.LikkaState
import com.likkapet.domain.model.MotionMode
import com.likkapet.domain.model.MotionPose
import com.likkapet.domain.model.OverlayMotion
import com.likkapet.domain.model.OverlayScene
import com.likkapet.domain.model.OverlayVisibility

/**
 * Turns the coordinator's [LikkaOverlayState] and the planner's [OverlayMotion] into the
 * [OverlayScene] the window shows (RF-O02–RF-O07). It only remembers the last level Likka was shown
 * at, so the farewell keeps Likka's size; everything else comes from its inputs.
 */
class OverlaySceneReducer {
    private var lastCompanionLevel = FIRST_LEVEL

    fun sceneFor(
        likkaState: LikkaState,
        state: LikkaOverlayState,
        motion: OverlayMotion?,
    ): OverlayScene {
        if (likkaState == LikkaState.EJECTED) return OverlayScene.Ejected(sendsHome = state.isWatchedAppInForeground)
        if (state.visibility != OverlayVisibility.SHOWN) return hiddenOrWithdrawn(state)
        return when {
            state.level == FURY_LEVEL -> fury(state)
            state.level >= FIRST_LEVEL -> companion(state, motion)
            state.isFarewell -> resting(MotionPose.GOODBYE, lastCompanionLevel, motion)
            state.isPaused -> resting(MotionPose.SIT, FIRST_LEVEL, motion)
            else -> OverlayScene.Withdrawn
        }
    }

    // A hidden level or pause keeps its window for an instant return; a hidden level 0 has nothing to keep.
    private fun hiddenOrWithdrawn(state: LikkaOverlayState): OverlayScene =
        if (state.level >= FIRST_LEVEL || state.isPaused) OverlayScene.Hidden else OverlayScene.Withdrawn

    private fun fury(state: LikkaOverlayState): OverlayScene {
        lastCompanionLevel = LAST_COMPANION_LEVEL
        return OverlayScene.Fury(
            roast = state.roast,
            secondsLeft = state.level3SecondsLeft ?: EscalationConfig.LEVEL_3_AUTO_HOME_SEC,
        )
    }

    private fun companion(
        state: LikkaOverlayState,
        motion: OverlayMotion?,
    ): OverlayScene {
        lastCompanionLevel = state.level
        return OverlayScene.Companion(
            level = state.level,
            roast = state.roast,
            motion = motion?.takeIf { it.mode != MotionMode.RESTING },
        )
    }

    private fun resting(
        pose: MotionPose,
        sizeLevel: Int,
        motion: OverlayMotion?,
    ) = OverlayScene.Resting(pose, sizeLevel, motion?.takeIf { it.mode == MotionMode.RESTING })

    private companion object {
        const val FIRST_LEVEL = 1
        const val LAST_COMPANION_LEVEL = 2
        const val FURY_LEVEL = 3
    }
}
