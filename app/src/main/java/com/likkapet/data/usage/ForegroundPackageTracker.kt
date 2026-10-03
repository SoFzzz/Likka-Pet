package com.likkapet.data.usage

import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.port.WallClock
import java.util.concurrent.TimeUnit

/**
 * Keeps the last known foreground package between polls (RF-A01): a poll window without new
 * ACTIVITY_RESUMED events does not change it. The first poll looks back
 * [EscalationConfig.FOREGROUND_INITIAL_LOOKBACK_MIN] minutes so an app that is already open when the
 * service starts is found without waiting for the next app change.
 */
class ForegroundPackageTracker(
    private val reader: ResumedEventsReader,
    private val wallClock: WallClock,
) {
    private var lastQueryEndMs: Long? = null

    @Volatile
    var currentPackage: String? = null
        private set

    /** Reads the events since the previous poll and returns the foreground package, null if unknown. */
    @Synchronized
    fun poll(): String? {
        val nowMs = wallClock.nowEpochMillis()
        val fromMs = lastQueryEndMs?.let { minOf(it, nowMs) - OVERLAP_MS } ?: (nowMs - INITIAL_LOOKBACK_MS)
        // Overlapping the previous window by one period is harmless (the last event wins) and means
        // an event stamped right at the boundary is never missed.
        reader
            .read(fromMs, nowMs)
            .maxByOrNull { it.timestampMs }
            ?.let { currentPackage = it.packageName }
        lastQueryEndMs = nowMs
        return currentPackage
    }

    private companion object {
        val OVERLAP_MS = TimeUnit.SECONDS.toMillis(EscalationConfig.FOREGROUND_POLL_SEC.toLong())
        val INITIAL_LOOKBACK_MS = TimeUnit.MINUTES.toMillis(EscalationConfig.FOREGROUND_INITIAL_LOOKBACK_MIN.toLong())
    }
}
