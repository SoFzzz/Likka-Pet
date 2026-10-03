package com.likkapet.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager

/** [ResumedEventsReader] over `UsageStatsManager.queryEvents`; needs PACKAGE_USAGE_STATS. */
class UsageStatsResumedEventsReader(
    private val usageStatsManager: UsageStatsManager,
) : ResumedEventsReader {
    override fun read(
        fromMs: Long,
        toMs: Long,
    ): List<ResumedEvent> {
        val events = usageStatsManager.queryEvents(fromMs, toMs)
        val event = UsageEvents.Event()
        return buildList {
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                // ACTIVITY_RESUMED is the only relevant event from API 29, the project's minSdk (RF-A01).
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) add(ResumedEvent(event.packageName, event.timeStamp))
            }
        }
    }
}
