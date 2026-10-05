package com.likkapet.data.roast

import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.TriggerReason

/**
 * Pool key `${app}_L${level}_${reason}` (RF-I06). The reason is part of the key because either track
 * can reach a level first, and every added app shares the `OTHER_...` keys (RF-I10).
 */
data class RoastKey(
    val app: TargetApp,
    val level: Int,
    val reason: TriggerReason,
) {
    val id: String get() = "${app.name}_L${level}_${reason.name}"
}

/**
 * In-memory roasts waiting to be shown, at most [EscalationConfig.ROAST_POOL_SIZE] per key. Each one
 * is served once and then forgotten, so the same roast never shows twice for a key (RF-I06).
 * Not thread-safe: the owner synchronizes.
 */
class RoastPool {
    private val roasts = mutableMapOf<RoastKey, ArrayDeque<String>>()

    fun size(key: RoastKey): Int = roasts[key]?.size ?: 0

    /** False when the key is full or already holds that exact text. */
    fun add(
        key: RoastKey,
        roast: String,
    ): Boolean {
        val queue = roasts.getOrPut(key) { ArrayDeque() }
        if (queue.size >= EscalationConfig.ROAST_POOL_SIZE || roast in queue) return false
        queue.addLast(roast)
        return true
    }

    fun take(key: RoastKey): String? = roasts[key]?.removeFirstOrNull()

    fun clear() = roasts.clear()
}
