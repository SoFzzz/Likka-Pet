package com.likkapet.domain

import com.likkapet.domain.model.PauseResult

/**
 * Rule E (documentación §3.2, RF-S01), shared by [EscalationCoordinator] and the dashboard so the
 * limits live in one place.
 */
object PauseRules {
    /** Why a pause cannot start, or null when it can. */
    fun rejectionFor(
        isPaused: Boolean,
        isEjected: Boolean,
        hasTrackAtLevel3: Boolean,
        pausesUsedToday: Int,
    ): PauseResult? =
        when {
            isPaused -> PauseResult.REJECTED_ALREADY_PAUSED
            isEjected -> PauseResult.REJECTED_WHILE_EJECTED
            hasTrackAtLevel3 -> PauseResult.REJECTED_LEVEL_3
            pausesUsedToday >= EscalationConfig.MAX_PAUSES_PER_DAY -> PauseResult.REJECTED_DAILY_LIMIT
            else -> null
        }

    fun pausesLeft(pausesUsedToday: Int): Int = (EscalationConfig.MAX_PAUSES_PER_DAY - pausesUsedToday).coerceAtLeast(0)
}
