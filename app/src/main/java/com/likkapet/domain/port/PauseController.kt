package com.likkapet.domain.port

import com.likkapet.domain.model.PauseResult
import kotlinx.coroutines.flow.Flow

/**
 * How the dashboard pauses Likka (rule E, RF-S01). While the service runs, the request goes through
 * the live `EscalationCoordinator`, which knows about Level 3 and ejections; with the service off
 * there is no session to protect and the stored state decides.
 */
interface PauseController {
    /** True while the overlay shows Level 3, when no pause can start (rule E). */
    val isLevel3Active: Flow<Boolean>

    /** Counts the pause and sets `paused_until` only when the result is `PauseResult.ACCEPTED`. */
    suspend fun requestPause(minutes: Int): PauseResult

    suspend fun resume()
}
