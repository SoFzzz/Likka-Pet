package com.likkapet.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.likkapet.LikkaApplication
import com.likkapet.domain.shouldStartMonitoring
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * Starts the service again after the phone boots (documentación §9.3, RNF-F02). BOOT_COMPLETED is one
 * of Android 12's exceptions to "no foreground service from the background". Only that action is
 * handled, not LOCKED_BOOT_COMPLETED: before the first unlock the DataStore (credential-protected
 * storage) cannot be read, so the user's settings are unknown. On MIUI it needs Autostart (RF-A05).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as LikkaApplication
        val pending = goAsync()
        app.launchInBackground {
            try {
                startIfActive(app)
            } catch (e: IOException) {
                Log.w(TAG, "Boot start skipped: settings unreadable (${e::class.simpleName})")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun startIfActive(app: LikkaApplication) {
        val settings =
            app.statsStore.snapshot
                .first()
                .settings
        val shouldStart = shouldStartMonitoring(settings.onboardingCompleted, settings.likkaEnabled, app.hasAllPermissions())
        Log.i(TAG, "Boot completed: startService=$shouldStart")
        if (shouldStart) app.monitoringController.start()
    }

    private companion object {
        const val TAG = "LikkaBoot"
    }
}
