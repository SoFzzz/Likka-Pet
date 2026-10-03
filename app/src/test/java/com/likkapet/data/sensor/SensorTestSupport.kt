package com.likkapet.data.sensor

import com.likkapet.domain.model.Vector3
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

const val NANOS_PER_SECOND = 1_000_000_000L

/** Event timestamps of a sensor that delivers [hz] samples per second for [seconds] seconds. */
fun timestampsAt(
    hz: Int,
    seconds: Int,
): List<Long> = (0 until hz * seconds).map { it * NANOS_PER_SECOND / hz }

// Phone upright in front of the face (θ = 90°) and flat on its back (θ = 0°), in m/s².
val UPRIGHT = Vector3(0.0, 9.81, 0.0)
val FLAT = Vector3(0.0, 0.0, 9.81)

fun gravity(
    timestampNanos: Long,
    vector: Vector3,
) = MotionSample(MotionSensorKind.GRAVITY, timestampNanos, vector)

fun accelerometer(
    timestampNanos: Long,
    vector: Vector3,
) = MotionSample(MotionSensorKind.ACCELEROMETER, timestampNanos, vector)

/** A [MotionSensorFeed] the test drives by hand; it counts how many collectors are "registered". */
class FakeMotionFeed(
    override val hasGravity: Boolean = true,
) : MotionSensorFeed {
    private val events = MutableSharedFlow<MotionSample>(extraBufferCapacity = 4096)

    var registrations = 0
        private set
    var activeListeners = 0
        private set

    override fun samples(): Flow<MotionSample> =
        events
            .onStart {
                registrations++
                activeListeners++
            }.onCompletion { activeListeners-- }

    fun deliver(sample: MotionSample) = check(events.tryEmit(sample))
}
