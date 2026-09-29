package com.likkapet.domain.port

import com.likkapet.domain.model.AiServiceStatus
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.TriggerReason
import kotlinx.coroutines.flow.StateFlow

/**
 * Source of the roasts Likka says (documentación §6 Módulo 4). The provider stays behind this
 * interface: changing model or provider happens in the Worker, not here.
 */
interface RoastGenerator {
    val serviceStatus: StateFlow<AiServiceStatus>

    /**
     * Asks in the background for one more roast of this level, so it is ready by the time it is
     * needed. Never waits for the network; makes no request with AI off (RF-I09).
     */
    fun prefetch(
        app: TargetApp,
        level: Int,
        reason: TriggerReason,
    )

    /** A pooled roast, used once, or a local one when the pool is empty. Never waits for the network. */
    suspend fun nextRoast(
        app: TargetApp,
        level: Int,
        reason: TriggerReason,
    ): String

    /** Empties the in-memory pool of prefetched roasts (e.g. when app language changes, RF-S09). */
    fun clearPool() {}
}
