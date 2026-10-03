package com.likkapet.data.sensor

import kotlinx.coroutines.flow.Flow

/** The Android sensor stack behind [AndroidPostureSource], as an interface so JVM tests can feed it events. */
interface MotionSensorFeed {
    val hasGravity: Boolean

    /**
     * Every event of the sensors the source needs, at whatever rate the device delivers. Cold:
     * the listeners are registered when collection starts and unregistered when it ends.
     */
    fun samples(): Flow<MotionSample>
}
