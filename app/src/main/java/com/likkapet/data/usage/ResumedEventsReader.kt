package com.likkapet.data.usage

/** An activity that came to the foreground, from `UsageEvents.Event.ACTIVITY_RESUMED`. */
data class ResumedEvent(
    val packageName: String,
    val timestampMs: Long,
)

/** Reads ACTIVITY_RESUMED events in a time window; wall-clock milliseconds, as UsageStatsManager uses. */
fun interface ResumedEventsReader {
    fun read(
        fromMs: Long,
        toMs: Long,
    ): List<ResumedEvent>
}
