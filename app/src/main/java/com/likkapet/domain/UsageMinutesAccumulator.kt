package com.likkapet.domain

import com.likkapet.domain.port.Clock

/**
 * RF-D02: usage time adds up in memory on every poll (about every 2 s) and reaches the store only
 * every [EscalationConfig.USAGE_FLUSH_SEC] and when the screen turns off, in whole minutes. The
 * leftover seconds stay in memory for the next flush. The poller wiring arrives with the service.
 */
class UsageMinutesAccumulator(
    private val clock: Clock,
    private val writeMinutes: (Int) -> Unit,
) {
    private var pendingMs = 0L
    private var lastFlushMs = clock.nowMillis()

    fun addUsage(elapsedMs: Long) {
        pendingMs += elapsedMs
        if (clock.nowMillis() - lastFlushMs >= EscalationTimings.USAGE_FLUSH_MS) flush()
    }

    fun onScreenOff() = flush()

    private fun flush() {
        lastFlushMs = clock.nowMillis()
        val minutes = (pendingMs / EscalationTimings.MILLIS_PER_MINUTE).toInt()
        if (minutes == 0) return
        pendingMs -= minutes * EscalationTimings.MILLIS_PER_MINUTE
        writeMinutes(minutes)
    }
}
