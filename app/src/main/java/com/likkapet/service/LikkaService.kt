package com.likkapet.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ServiceCompat
import com.likkapet.LikkaApplication
import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.model.AppLanguage
import com.likkapet.domain.shouldStartMonitoring
import com.likkapet.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * The app's only foreground service (documentación §9.3). It owns one [MonitoringSession] while it
 * runs, shows its state with an [OverlayController] (Likka over the watched app, documentación §9.1)
 * and keeps the persistent notification up to date. Logs carry enum names and numbers only, never
 * an app name, package or roast.
 *
 * Android 12+ forbids starting a foreground service from the background, with exceptions: the app
 * on screen (`MainActivity`), `BOOT_COMPLETED` (`BootReceiver`) and a tap on the notification's
 * action. A `START_STICKY` restart by the system is not guaranteed to be allowed.
 */
class LikkaService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + loggingExceptionHandler(TAG))

    @Volatile private var session: MonitoringSession? = null

    @Volatile private var overlayController: OverlayController? = null
    private var overlayJob: Job? = null
    private var notificationJob: Job? = null
    private var languageJob: Job? = null

    @Volatile private var currentLanguageCode: String =
        AppCompatDelegate.getApplicationLocales()[0]?.language?.takeIf { it.isNotEmpty() }?.let {
            if (it.startsWith("en", ignoreCase = true)) "en" else "es"
        } ?: AppLanguage.SYSTEM.resolve()

    // Set once the service is going away, so a coroutine still on its way cannot start a session
    // nobody would ever stop, nor re-post the notification that stopForeground removed.
    @Volatile private var isEnding = false
    private var startedAtElapsedMs = 0L
    private var heartbeatCount = 0
    private var lastHeartbeatElapsedMs = 0L

    // Written by the notification collector (Default threads), read by onStartCommand (main thread).
    @Volatile private var notificationState = GuardianNotificationState.INITIAL

    private val app: LikkaApplication get() = application as LikkaApplication

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        GuardianNotification.createChannel(localizedContext(currentLanguageCode))
        startedAtElapsedMs = SystemClock.elapsedRealtime()
        lastHeartbeatElapsedMs = startedAtElapsedMs
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Every start command must reach startForeground(), even a stop request (see ACTION_STOP).
        if (!startAsForeground(startId)) return START_STICKY
        if (intent?.action == ACTION_STOP) {
            stopUnlessNewerStartQueued(startId)
            return START_STICKY
        }
        // A null intent is the system restarting the sticky service: the user may have switched
        // Likka off or revoked a permission since, so the conditions are checked again.
        if (intent == null) startSessionIfStillWanted(startId) else startSessionOnce()
        if (intent?.action == ACTION_PAUSE) pauseFromNotification()
        return START_STICKY
    }

    // Rotation: the overlay needs the new screen size and insets (RF-O13).
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayController?.onConfigurationChanged()
    }

    @Synchronized
    override fun onDestroy() {
        isEnding = true
        overlayJob?.cancel()
        overlayJob = null
        notificationJob?.cancel()
        notificationJob = null
        languageJob?.cancel()
        languageJob = null
        // Main thread: the window goes before the session that feeds it.
        overlayController?.stop()
        overlayController = null
        session?.let { ended ->
            app.pauseController.detach(ended)
            // The service scope dies with this call; the final flush of the minutes must outlive it.
            app.stopMonitoringSession(ended)
        }
        session = null
        serviceScope.cancel()
        Log.i(TAG, "Service stopped")
        super.onDestroy()
    }

    /**
     * The "health" type only exists on API 34+; on API 29–33 the service starts without a type.
     * Returns false when Android 12+ refuses the start (a `START_STICKY` restart from the
     * background, for instance): the service then ends quietly and the next time the app is opened
     * `MainActivity` starts it again from the foreground.
     */
    private fun startAsForeground(startId: Int): Boolean =
        try {
            ServiceCompat.startForeground(
                this,
                GuardianNotification.NOTIFICATION_ID,
                GuardianNotification.build(localizedContext(currentLanguageCode), notificationState),
                foregroundServiceType(),
            )
            true
        } catch (e: IllegalStateException) {
            if (!isStartNotAllowed(e)) throw e
            Log.w(TAG, "Foreground start not allowed from the background; stopping the service", e)
            stopSelf(startId)
            false
        }

    private fun isStartNotAllowed(e: IllegalStateException): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException

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
            // A late notification update would re-post the ongoing notification after stopForeground removed it.
            isEnding = true
            overlayJob?.cancel()
            overlayJob = null
            notificationJob?.cancel()
            notificationJob = null
            languageJob?.cancel()
            languageJob = null
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    private fun startSessionIfStillWanted(startId: Int) {
        serviceScope.launch {
            if (isStillWanted()) {
                startSessionOnce()
            } else {
                Log.i(TAG, "Sticky restart ignored: Likka is off, a permission is missing or the settings are unreadable")
                stopSelf(startId)
            }
        }
    }

    // Unreadable settings count as "not wanted": the user can reopen the app, which starts it from the foreground.
    private suspend fun isStillWanted(): Boolean =
        try {
            val settings =
                app.statsStore.snapshot
                    .first()
                    .settings
            shouldStartMonitoring(settings.onboardingCompleted, settings.likkaEnabled, app.hasAllPermissions())
        } catch (e: IOException) {
            Log.w(TAG, "Settings unreadable: ${e::class.simpleName}")
            false
        }

    @Synchronized
    private fun startSessionOnce() {
        if (session != null || isEnding) return
        val running = app.createMonitoringSession(serviceScope)
        session = running
        app.pauseController.attach(running)
        running.start()
        // The overlay's window, context and screen metrics belong to the main thread.
        overlayJob = serviceScope.launch(Dispatchers.Main.immediate) { runOverlay(running) }
        notificationJob = serviceScope.launch { keepNotificationUpToDate(running) }
        serviceScope.launch { runHeartbeat(running) }
        languageJob = serviceScope.launch { observeLanguageChanges(running) }
    }

    private suspend fun runOverlay(running: MonitoringSession) {
        if (isEnding) return
        val controller = createOverlayController(running, currentLanguageCode)
        overlayController = controller
        try {
            controller.run()
        } finally {
            if (overlayController === controller) overlayController = null
        }
    }

    private fun createOverlayController(
        running: MonitoringSession,
        languageCode: String,
    ) = OverlayController(
        context = OverlayWindow.overlayContext(localizedContext(languageCode)),
        feeds = running.overlayFeed,
        planner = app.createOverlayMotionPlanner(),
        reactions = app.createLocalReactionDisplay(languageCode),
        onSurrenderClick = running::onSurrenderClick,
        openApp = ::openApp,
        log = { message -> Log.i(OVERLAY_TAG, message) },
    )

    // Long press on Level 1 (RF-O02); allowed from the background because the overlay is visible.
    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // "Pausar 30 min" goes through the same rule E as the dashboard. A rejection leaves the
    // notification as it is: the action is hidden whenever the rule would refuse.
    private fun pauseFromNotification() {
        serviceScope.launch {
            val result = app.pauseController.requestPause(EscalationConfig.NOTIFICATION_PAUSE_MIN)
            Log.i(TAG, "Pause from notification: $result")
        }
    }

    private suspend fun keepNotificationUpToDate(running: MonitoringSession) {
        val permissionsGranted =
            app.permissionChecker.watchAllGranted(EscalationConfig.PERMISSION_CHECK_SEC.seconds.inWholeMilliseconds)
        combine(
            running.likkaState,
            running.overlayState,
            app.statsStore.snapshot,
            permissionsGranted,
            GuardianNotificationState::of,
        ).distinctUntilChanged()
            .collect { state ->
                if (isEnding) return@collect
                notificationState = state
                val context = localizedContext(currentLanguageCode)
                getSystemService(NotificationManager::class.java)
                    .notify(GuardianNotification.NOTIFICATION_ID, GuardianNotification.build(context, state))
            }
    }

    private suspend fun observeLanguageChanges(running: MonitoringSession) {
        app.statsStore.snapshot
            .map { it.settings.appLanguage.resolve() }
            .distinctUntilChanged()
            .collect { resolvedLang ->
                if (isEnding) return@collect
                val previousLang = currentLanguageCode
                currentLanguageCode = resolvedLang
                if (previousLang != resolvedLang) {
                    Log.i(TAG, "Language changed from $previousLang to $resolvedLang; updating service components")
                    val context = localizedContext(resolvedLang)
                    GuardianNotification.createChannel(context)
                    getSystemService(NotificationManager::class.java)
                        .notify(GuardianNotification.NOTIFICATION_ID, GuardianNotification.build(context, notificationState))
                    app.roastGenerator.clearPool()
                    restartOverlay(running)
                }
            }
    }

    private suspend fun restartOverlay(running: MonitoringSession) {
        overlayJob?.cancel()
        overlayJob?.join()
        if (isEnding) return
        overlayJob = serviceScope.launch(Dispatchers.Main.immediate) { runOverlay(running) }
    }

    private fun localizedContext(resolvedLang: String): Context {
        val config =
            Configuration(resources.configuration).apply {
                setLocale(Locale(resolvedLang))
            }
        return createConfigurationContext(config)
    }

    // delay() stops counting during deep sleep, so the real interval is measured with
    // elapsedRealtime: an interval far above the period means the CPU slept.
    private suspend fun runHeartbeat(running: MonitoringSession) {
        while (true) {
            delay(EscalationConfig.HEARTBEAT_SEC.seconds)
            logHeartbeat(running)
        }
    }

    private fun logHeartbeat(running: MonitoringSession) {
        val now = SystemClock.elapsedRealtime()
        val intervalMs = now - lastHeartbeatElapsedMs
        val elapsedMinutes = TimeUnit.MILLISECONDS.toMinutes(now - startedAtElapsedMs)
        val readings = running.takeReadingsCount()
        heartbeatCount++
        Log.i(
            TAG,
            String.format(
                Locale.ROOT,
                "Heartbeat #%d: minutes=%d intervalMs=%d readings=%d rateHz=%.1f",
                heartbeatCount,
                elapsedMinutes,
                intervalMs,
                readings,
                readings * MILLIS_PER_SECOND / intervalMs,
            ),
        )
        lastHeartbeatElapsedMs = now
    }

    companion object {
        /** Stop request routed through onStartCommand so startForeground() always runs first. */
        const val ACTION_STOP = "com.likkapet.action.STOP_SERVICE"

        /** The notification's "Pausar" action (RF-O08). */
        const val ACTION_PAUSE = "com.likkapet.action.PAUSE_FROM_NOTIFICATION"

        private const val TAG = "LikkaService"
        private const val OVERLAY_TAG = "LikkaOverlay"
        private const val NO_SERVICE_TYPE = 0
        private const val MILLIS_PER_SECOND = 1_000.0
    }
}
