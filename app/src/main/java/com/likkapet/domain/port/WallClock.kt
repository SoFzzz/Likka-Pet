package com.likkapet.domain.port

/**
 * Wall-clock time in epoch milliseconds, for calendar logic: `today_date`, the streak and
 * `paused_until` (RF-D01). Kept apart from [Clock], which is monotonic (`elapsedRealtime`, §9.5)
 * and says nothing about the day, so the two cannot be mixed up by type.
 */
fun interface WallClock {
    fun nowEpochMillis(): Long
}
