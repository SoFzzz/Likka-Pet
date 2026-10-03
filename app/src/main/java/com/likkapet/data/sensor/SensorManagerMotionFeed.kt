package com.likkapet.data.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.model.Vector3
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [MotionSensorFeed] over [SensorManager]: TYPE_GRAVITY when the device has it (the Redmi 9 does,
 * a hardware sensor) plus the raw TYPE_ACCELEROMETER, asked at 200 ms (RF-P01). The period is only a
 * hint: events arrive at whatever rate the HAL decides and [PostureSamplePipeline] decimates them.
 */
class SensorManagerMotionFeed(
    private val sensorManager: SensorManager,
) : MotionSensorFeed {
    private val gravity: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    override val hasGravity: Boolean get() = gravity != null

    override fun samples(): Flow<MotionSample> =
        callbackFlow {
            // Own thread: the events never touch the main thread, and quitting it ends the callbacks.
            val thread = HandlerThread(THREAD_NAME).apply { start() }
            val listener = forwardingListener()
            val handler = Handler(thread.looper)
            val periodMicros = EscalationConfig.POSTURE_SAMPLE_PERIOD_MS * MICROS_PER_MILLI
            try {
                listOfNotNull(gravity, accelerometer).forEach { sensor ->
                    sensorManager.registerListener(listener, sensor, periodMicros, handler)
                }
            } catch (e: RuntimeException) {
                thread.quitSafely() // Do not leak the thread when registering fails; the flow ends with the error.
                throw e
            }
            awaitClose {
                sensorManager.unregisterListener(listener)
                thread.quitSafely()
            }
        }

    private fun ProducerScope<MotionSample>.forwardingListener() =
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val kind = kindOf(event.sensor.type) ?: return
                val (x, y, z) = event.values
                trySend(MotionSample(kind, event.timestamp, Vector3(x.toDouble(), y.toDouble(), z.toDouble())))
            }

            override fun onAccuracyChanged(
                sensor: Sensor,
                accuracy: Int,
            ) = Unit
        }

    private fun kindOf(sensorType: Int): MotionSensorKind? =
        when (sensorType) {
            Sensor.TYPE_GRAVITY -> MotionSensorKind.GRAVITY
            Sensor.TYPE_ACCELEROMETER -> MotionSensorKind.ACCELEROMETER
            else -> null
        }

    private companion object {
        const val THREAD_NAME = "LikkaSensors"
        const val MICROS_PER_MILLI = 1_000
    }
}
