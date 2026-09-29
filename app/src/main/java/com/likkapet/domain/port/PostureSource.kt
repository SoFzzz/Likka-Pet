package com.likkapet.domain.port

import com.likkapet.domain.model.PostureReading
import kotlinx.coroutines.flow.Flow

/** Posture samples already at about 5 Hz, whatever rate the sensor really delivers (RF-P01). */
interface PostureSource {
    /**
     * Cold flow: the sensors are registered while it is collected and released when the
     * collection ends or the screen turns off (RF-P05).
     */
    val readings: Flow<PostureReading>
}
