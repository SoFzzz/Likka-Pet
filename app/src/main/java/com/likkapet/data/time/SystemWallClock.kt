package com.likkapet.data.time

import com.likkapet.domain.port.WallClock

/** Calendar time for `today_date` and `paused_until`; the monotonic escalation clock is a different port. */
object SystemWallClock : WallClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
