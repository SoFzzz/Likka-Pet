package com.likkapet.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.likkapet.domain.port.MonitoringController

/** Must be started from the foreground (MainActivity): Android 12+ blocks background starts (documentación §9.3). */
class ServiceMonitoringController(
    private val context: Context,
) : MonitoringController {
    override fun start() = startService(serviceIntent())

    // Stop goes through the same command queue instead of stopService(): a stop that arrived
    // before onStartCommand would destroy the service before startForeground() and crash the app.
    override fun stop() {
        startService(serviceIntent().setAction(LikkaService.ACTION_STOP))
    }

    // Android 12+ refuses it when the app is not allowed to start a foreground service at that
    // moment (e.g. the app left the screen between the check and the call); that is not a crash.
    private fun startService(intent: Intent) {
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: IllegalStateException) {
            if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException)) throw e
            Log.w(TAG, "Service start not allowed from the background", e)
        }
    }

    private fun serviceIntent() = Intent(context, LikkaService::class.java)

    private companion object {
        const val TAG = "LikkaService"
    }
}
