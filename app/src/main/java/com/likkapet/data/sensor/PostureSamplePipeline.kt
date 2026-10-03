package com.likkapet.data.sensor

import com.likkapet.domain.BiomechanicsCalculator
import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.model.PostureReading
import com.likkapet.domain.model.Vector3
import java.util.concurrent.TimeUnit

/**
 * From raw sensor events to [PostureReading]s at ~5 Hz (documentación §4, RF-P01–RF-P03).
 *
 * The angle comes from TYPE_GRAVITY, or from the accelerometer smoothed with an EMA when the
 * device has no gravity sensor. The on-table check always needs the raw accelerometer magnitude
 * (gravity is already filtered and would hide the tremor, §4.1). Both streams are decimated first,
 * so the window of [EscalationConfig.TABLE_WINDOW_SAMPLES] samples really spans 2 s and the EMA
 * alpha is tuned for 5 Hz. Not thread-safe: feed it from one place.
 */
class PostureSamplePipeline(
    private val hasGravity: Boolean,
) {
    private val periodNanos = TimeUnit.MILLISECONDS.toNanos(EscalationConfig.POSTURE_SAMPLE_PERIOD_MS.toLong())
    private val jitterNanos = TimeUnit.MILLISECONDS.toNanos(EscalationConfig.POSTURE_SAMPLE_JITTER_MS.toLong())
    private val gravityDecimator = SampleDecimator(periodNanos, jitterNanos)
    private val accelerometerDecimator = SampleDecimator(periodNanos, jitterNanos)
    private val rawMagnitudes = ArrayDeque<Double>()
    private var smoothedAcceleration: Vector3? = null

    /** The reading this event completes, or null when it was decimated away, unusable or only feeds the window. */
    fun onSample(sample: MotionSample): PostureReading? =
        when {
            !sample.vector.isFinite() -> null
            sample.kind == MotionSensorKind.GRAVITY && hasGravity -> onGravity(sample)
            sample.kind == MotionSensorKind.ACCELEROMETER -> onAccelerometer(sample)
            else -> null
        }

    private fun onGravity(sample: MotionSample): PostureReading? {
        if (!gravityDecimator.accepts(sample.timestampNanos)) return null
        return BiomechanicsCalculator.postureReading(sample.vector, rawMagnitudes.toList())
    }

    private fun onAccelerometer(sample: MotionSample): PostureReading? {
        if (!accelerometerDecimator.accepts(sample.timestampNanos)) return null
        recordMagnitude(BiomechanicsCalculator.magnitude(sample.vector))
        if (hasGravity) return null
        val smoothed = smooth(sample.vector)
        return BiomechanicsCalculator.postureReading(smoothed, rawMagnitudes.toList())
    }

    private fun recordMagnitude(magnitude: Double) {
        rawMagnitudes.addLast(magnitude)
        if (rawMagnitudes.size > EscalationConfig.TABLE_WINDOW_SAMPLES) rawMagnitudes.removeFirst()
    }

    private fun smooth(sample: Vector3): Vector3 {
        val previous = smoothedAcceleration
        val alpha = EscalationConfig.ACCELEROMETER_EMA_ALPHA
        val next =
            if (previous == null) {
                sample
            } else {
                Vector3(
                    x = alpha * sample.x + (1 - alpha) * previous.x,
                    y = alpha * sample.y + (1 - alpha) * previous.y,
                    z = alpha * sample.z + (1 - alpha) * previous.z,
                )
            }
        smoothedAcceleration = next
        return next
    }

    private fun Vector3.isFinite() = x.isFinite() && y.isFinite() && z.isFinite()
}
