package com.likkapet.domain

import com.likkapet.domain.model.PostureReading
import com.likkapet.domain.model.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Pure posture math of documentación §4: screen tilt θ and the on-table classification (§4.1).
 *
 * The on-table window is the last [EscalationConfig.TABLE_WINDOW_SAMPLES] raw accelerometer
 * magnitudes, and that constant means 2 s only at 5 Hz. Android treats the requested sensor period
 * as a hint (the emulator delivers 20 Hz for a 200 ms request), so the posture source must decimate
 * by sample timestamp before filling the window; this class cannot tell how old the samples are.
 */
object BiomechanicsCalculator {
    private const val DEGREES_PER_RADIAN = 180.0 / PI

    fun magnitude(vector: Vector3): Double = sqrt(vector.x * vector.x + vector.y * vector.y + vector.z * vector.z)

    /**
     * θ = arccos(z / |v|) in degrees: 90° upright in front of the face, 0° flat face up (RF-P02).
     * Returns null for a vector without direction (zero or non-finite length), which the source
     * must drop instead of reporting a made-up angle.
     */
    fun screenTiltDegrees(orientation: Vector3): Double? {
        val length = magnitude(orientation)
        if (length == 0.0 || !length.isFinite()) return null
        return angleFromCosine(orientation.z / length)
    }

    /** Population standard deviation (÷ N): the window is the whole population being judged. */
    fun standardDeviation(values: List<Double>): Double {
        require(values.isNotEmpty()) { "Standard deviation of no values" }
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }

    /**
     * §4.1 triple condition. [orientation] is the vector the angle comes from (TYPE_GRAVITY, or the
     * EMA-filtered accelerometer without it); [rawMagnitudes] are |a| of the raw accelerometer,
     * because gravity is already filtered and would hide the hand tremor. A window with fewer than
     * [EscalationConfig.TABLE_WINDOW_SAMPLES] samples is not enough evidence: phone-on-the-lap is
     * the worst posture, so doubt never freezes the POSTURE track.
     */
    fun isOnTable(
        orientation: Vector3,
        rawMagnitudes: List<Double>,
    ): Boolean {
        if (rawMagnitudes.size < EscalationConfig.TABLE_WINDOW_SAMPLES) return false
        val isFlat = orientation.z > EscalationConfig.TABLE_MIN_Z && abs(orientation.y) < EscalationConfig.TABLE_MAX_ABS_Y
        return isFlat && isStill(rawMagnitudes.takeLast(EscalationConfig.TABLE_WINDOW_SAMPLES))
    }

    /** The reading for the coordinator, or null when the orientation sample must be dropped. */
    fun postureReading(
        orientation: Vector3,
        rawMagnitudes: List<Double>,
    ): PostureReading? =
        screenTiltDegrees(orientation)?.let { angle ->
            PostureReading(angleDegrees = angle, isOnTable = isOnTable(orientation, rawMagnitudes))
        }

    // Rounding can push the cosine just past ±1 (e.g. with subnormal components); acos would be NaN.
    internal fun angleFromCosine(cosine: Double): Double = acos(cosine.coerceIn(-1.0, 1.0)) * DEGREES_PER_RADIAN

    private fun isStill(window: List<Double>): Boolean = standardDeviation(window) < EscalationConfig.TABLE_MAX_STDDEV
}
