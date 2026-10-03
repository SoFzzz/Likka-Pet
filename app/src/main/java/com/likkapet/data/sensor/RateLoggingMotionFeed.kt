package com.likkapet.data.sensor

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.util.Locale

/**
 * Debug decorator: logs how many raw events per second each sensor really delivers, which is what
 * RNF-P02 has to be measured at (documentación §6) and what the decimation hides. It only counts;
 * no sample value is ever logged.
 */
class RateLoggingMotionFeed(
    private val inner: MotionSensorFeed,
    private val logIntervalMs: Long = LOG_INTERVAL_MS,
) : MotionSensorFeed {
    override val hasGravity: Boolean get() = inner.hasGravity

    override fun samples(): Flow<MotionSample> {
        val counts = MotionSensorKind.entries.associateWith { 0 }.toMutableMap()
        var windowStartNanos: Long? = null
        return inner.samples().onEach { sample ->
            counts[sample.kind] = counts.getValue(sample.kind) + 1
            val start = windowStartNanos ?: sample.timestampNanos.also { windowStartNanos = it }
            val elapsedMs = (sample.timestampNanos - start) / NANOS_PER_MILLI
            if (elapsedMs >= logIntervalMs) {
                logRates(counts, elapsedMs)
                counts.keys.forEach { counts[it] = 0 }
                windowStartNanos = sample.timestampNanos
            }
        }
    }

    private fun logRates(
        counts: Map<MotionSensorKind, Int>,
        elapsedMs: Long,
    ) {
        val rates =
            counts.entries.joinToString(" ") { (kind, count) ->
                String.format(Locale.ROOT, "%s=%.1fHz", kind, count * MILLIS_PER_SECOND / elapsedMs)
            }
        Log.d(TAG, "Raw sensor delivery over ${elapsedMs}ms: $rates")
    }

    private companion object {
        const val TAG = "LikkaPostureRate"
        const val LOG_INTERVAL_MS = 5_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
