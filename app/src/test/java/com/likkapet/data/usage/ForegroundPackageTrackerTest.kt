package com.likkapet.data.usage

import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.port.WallClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

/** RF-A01: last known app kept between polls, and a 10 minute look-back on the first poll. */
class ForegroundPackageTrackerTest {
    private var nowMs = 10_000_000L
    private val events = mutableListOf<ResumedEvent>()
    private val queries = mutableListOf<LongRange>()
    private val tracker =
        ForegroundPackageTracker(
            reader =
                ResumedEventsReader { from, to ->
                    queries += from..to
                    events.filter { it.timestampMs in from..to }
                },
            wallClock = WallClock { nowMs },
        )

    private fun resumed(
        packageName: String,
        atMs: Long = nowMs,
    ) {
        events += ResumedEvent(packageName, atMs)
    }

    @Test
    fun `the first poll looks back far enough to find an app that was already open`() {
        resumed("com.zhiliaoapp.musically", atMs = nowMs - TimeUnit.MINUTES.toMillis(8))

        assertEquals("com.zhiliaoapp.musically", tracker.poll())
        val lookbackMs = queries.first().last - queries.first().first
        assertEquals(TimeUnit.MINUTES.toMillis(EscalationConfig.FOREGROUND_INITIAL_LOOKBACK_MIN.toLong()), lookbackMs)
    }

    @Test
    fun `an app opened longer ago than the look-back is unknown`() {
        resumed("com.zhiliaoapp.musically", atMs = nowMs - TimeUnit.MINUTES.toMillis(11))

        assertNull(tracker.poll())
    }

    @Test
    fun `a window without new events keeps the last known app`() {
        resumed("com.google.android.youtube")
        tracker.poll()

        nowMs += TimeUnit.SECONDS.toMillis(2)
        assertEquals("com.google.android.youtube", tracker.poll())
        nowMs += TimeUnit.SECONDS.toMillis(2)
        assertEquals("com.google.android.youtube", tracker.poll())
    }

    @Test
    fun `the latest resume wins even if the reader returns them out of order`() {
        resumed("com.instagram.android", atMs = nowMs - 3_000)
        resumed("com.google.android.youtube", atMs = nowMs - 1_000)
        resumed("com.android.chrome", atMs = nowMs - 2_000)

        assertEquals("com.google.android.youtube", tracker.poll())
    }

    @Test
    fun `switching app is seen on the next poll`() {
        resumed("com.google.android.youtube")
        tracker.poll()

        nowMs += TimeUnit.SECONDS.toMillis(2)
        resumed("com.miui.home")

        assertEquals("com.miui.home", tracker.poll())
    }

    @Test
    fun `each later poll reads from just before the previous one, so nothing at the boundary is missed`() {
        tracker.poll()
        val firstEnd = queries.last().last
        nowMs += TimeUnit.SECONDS.toMillis(2)

        tracker.poll()

        val second = queries.last()
        assertEquals(nowMs, second.last)
        assertEquals(true, second.first < firstEnd)
    }

    @Test
    fun `a wall clock that jumps back does not break the poll`() {
        resumed("com.google.android.youtube")
        tracker.poll()

        nowMs -= TimeUnit.HOURS.toMillis(1)

        assertEquals("com.google.android.youtube", tracker.poll())
    }
}
