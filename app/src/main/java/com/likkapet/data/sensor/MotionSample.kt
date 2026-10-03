package com.likkapet.data.sensor

import com.likkapet.domain.model.Vector3

enum class MotionSensorKind { GRAVITY, ACCELEROMETER }

/** One raw sensor event; [timestampNanos] is the sensor's own clock, the only time decimation trusts. */
data class MotionSample(
    val kind: MotionSensorKind,
    val timestampNanos: Long,
    val vector: Vector3,
)
