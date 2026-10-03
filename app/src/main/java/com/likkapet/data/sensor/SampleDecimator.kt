package com.likkapet.data.sensor

/**
 * Thins a sensor stream down to one sample per period by the samples' own timestamps (RF-P01):
 * Android treats the requested period as a hint (the Redmi 9 delivers ~48 Hz for a 200 ms
 * request). Counting events or reading the wall clock would drift with the real rate, and the
 * on-table window assumes 5 Hz.
 *
 * Due times advance on a fixed grid, so the average rate stays exactly 1/period whatever the
 * delivery rate; a sample up to [jitterNanos] early still counts, otherwise a sensor that really
 * delivers 5 Hz with a little jitter would lose every other sample.
 */
class SampleDecimator(
    private val periodNanos: Long,
    private val jitterNanos: Long,
) {
    private var dueNanos: Long? = null

    fun accepts(timestampNanos: Long): Boolean {
        val due = dueNanos
        if (due != null && timestampNanos + jitterNanos < due) return false
        // Starved for a whole period (e.g. after a pause): start a new grid instead of firing a burst.
        val isStarved = due == null || timestampNanos - due >= periodNanos
        dueNanos = if (isStarved) timestampNanos + periodNanos else due + periodNanos
        return true
    }
}
