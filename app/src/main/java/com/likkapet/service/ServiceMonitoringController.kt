package com.likkapet.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.likkapet.domain.port.MonitoringController

/** Must be started from the foreground (MainActivity): Android 12+ blocks background starts (documentación §9.3). */
class ServiceMonitoringController(
    private val context: Context,
) : MonitoringController {
    override fun start() {
        ContextCompat.startForegroundService(context, serviceIntent())
    }

    // Stop goes through the same command queue instead of stopService(): a stop that arrived
    // before onStartCommand would destroy the service before startForeground() and crash the app.
    override fun stop() {
        ContextCompat.startForegroundService(context, serviceIntent().setAction(LikkaService.ACTION_STOP))
    }

    private fun serviceIntent() = Intent(context, LikkaService::class.java)
}
