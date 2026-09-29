package com.likkapet.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The app's only foreground service (documentación §9.3). Spike version (§14): it reads the
 * posture sensor at 5 Hz and logs a heartbeat, with no overlay, AI or DataStore yet.
 */
class LikkaService : Service() {
    private lateinit var sensorManager: SensorManager
    private lateinit var sensorThread: HandlerThread
    private lateinit var sensorHandler: Handler
    private var startedAtElapsedMs = 0L

    // Only read and written on sensorThread (sensor callbacks and the heartbeat both run there).
    private var lastHeartbeatElapsedMs = 0L
    private var heartbeatCount = 0
    private var readingsSinceHeartbeat = 0

    private val sensorListener =
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                readingsSinceHeartbeat++
            }

            override fun onAccuracyChanged(
                sensor: Sensor,
                accuracy: Int,
            ) = Unit
        }

    private val heartbeat =
        object : Runnable {
            override fun run() {
                logHeartbeat()
                sensorHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        GuardianNotification.createChannel(this)
        startedAtElapsedMs = SystemClock.elapsedRealtime()
        lastHeartbeatElapsedMs = startedAtElapsedMs
        startSensorThread()
        registerPostureSensor()
        sensorHandler.postDelayed(heartbeat, HEARTBEAT_INTERVAL_MS)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Every start command must reach startForeground(), even a stop request (see ACTION_STOP).
        val isInForeground = startAsForeground(startId)
        if (isInForeground && intent?.action == ACTION_STOP) {
            stopUnlessNewerStartQueued(startId)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(sensorListener)
        sensorHandler.removeCallbacksAndMessages(null)
        sensorThread.quitSafely()
        Log.i(TAG, "Service stopped")
        super.onDestroy()
    }

    /**
     * The "health" type only exists on API 34+; on API 29–33 the service starts without a type.
     * Returns false when Android 12+ refuses the start, e.g. a START_STICKY restart from the
     * background (ForegroundServiceStartNotAllowedException extends IllegalStateException).
     */
    private fun startAsForeground(startId: Int): Boolean =
        try {
            ServiceCompat.startForeground(
                this,
                GuardianNotification.NOTIFICATION_ID,
                GuardianNotification.build(this),
                foregroundServiceType(),
            )
            true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Foreground start not allowed; stopping the service", e)
            stopSelf(startId)
            false
        }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        } else {
            NO_SERVICE_TYPE
        }

    // stopSelfResult(startId) ignores the stop when a newer start command is already queued (e.g. a
    // quick Stop -> Start); stopping anyway would leave that start without startForeground() and crash.
    private fun stopUnlessNewerStartQueued(startId: Int) {
        if (stopSelfResult(startId)) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    private fun startSensorThread() {
        sensorThread = HandlerThread(SENSOR_THREAD_NAME).apply { start() }
        sensorHandler = Handler(sensorThread.looper)
    }

    /** RF-P01: TYPE_GRAVITY when the device has it, TYPE_ACCELEROMETER otherwise. */
    private fun registerPostureSensor() {
        sensorManager = getSystemService(SensorManager::class.java)
        val gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val sensor = gravity ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            Log.w(TAG, "No gravity or accelerometer sensor available")
            return
        }
        logChosenSensor(sensor, usedFallback = gravity == null)
        sensorManager.registerListener(sensorListener, sensor, SENSOR_SAMPLING_PERIOD_US, sensorHandler)
    }

    // Some vendors expose TYPE_GRAVITY as a virtual sensor without a gyroscope, so the spike
    // (§14) logs both facts separately.
    private fun logChosenSensor(
        sensor: Sensor,
        usedFallback: Boolean,
    ) {
        val hasGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        Log.i(
            TAG,
            "Posture sensor: ${sensor.stringType} name=${sensor.name} vendor=${sensor.vendor} " +
                "usedFallback=$usedFallback hasGyroscope=$hasGyroscope",
        )
    }

    // postDelayed stops counting during deep sleep, so the real interval is measured with
    // elapsedRealtime: an interval far above 60 s means the CPU slept.
    private fun logHeartbeat() {
        val now = SystemClock.elapsedRealtime()
        val intervalMs = now - lastHeartbeatElapsedMs
        val elapsedMinutes = TimeUnit.MILLISECONDS.toMinutes(now - startedAtElapsedMs)
        val readingsPerSecond = readingsSinceHeartbeat * MILLIS_PER_SECOND / intervalMs
        heartbeatCount++
        Log.i(
            TAG,
            String.format(
                Locale.ROOT,
                "Heartbeat #%d: minutes=%d intervalMs=%d readings=%d rateHz=%.1f",
                heartbeatCount,
                elapsedMinutes,
                intervalMs,
                readingsSinceHeartbeat,
                readingsPerSecond,
            ),
        )
        lastHeartbeatElapsedMs = now
        readingsSinceHeartbeat = 0
    }

    companion object {
        /** Stop request routed through onStartCommand so startForeground() always runs first. */
        const val ACTION_STOP = "com.likkapet.action.STOP_SERVICE"

        private const val TAG = "LikkaService"
        private const val SENSOR_THREAD_NAME = "LikkaSensors"
        private const val NO_SERVICE_TYPE = 0
        private const val MILLIS_PER_SECOND = 1_000.0

        // Spike-only diagnostics; the sampling period moves to its final home with AndroidPostureSource.
        private const val SENSOR_SAMPLING_PERIOD_US = 200_000 // 5 Hz (RF-P01)
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
    }
}
