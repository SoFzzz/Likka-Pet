package com.likkapet.service

import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** When the service next looks at the calendar day (RF-D01, RF-D04). */
object DayRollover {
    /** Milliseconds from [nowEpochMillis] to the next local midnight in [zone] (always positive). */
    fun millisUntilNextDay(
        nowEpochMillis: Long,
        zone: ZoneId,
    ): Long {
        val nextMidnight =
            Instant
                .ofEpochMilli(nowEpochMillis)
                .atZone(zone)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        return nextMidnight - nowEpochMillis
    }

    /** Waits up to the next midnight, but never longer than [maxWaitSec], so a clock or zone change is noticed. */
    fun nextCheckDelayMillis(
        nowEpochMillis: Long,
        zone: ZoneId,
        maxWaitSec: Int,
    ): Long = minOf(millisUntilNextDay(nowEpochMillis, zone), TimeUnit.SECONDS.toMillis(maxWaitSec.toLong()))
}
