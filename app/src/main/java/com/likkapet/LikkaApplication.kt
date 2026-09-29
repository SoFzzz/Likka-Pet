package com.likkapet

import android.app.Application
import android.app.usage.UsageStatsManager
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import com.likkapet.data.apps.PackageManagerAppIconLoader
import com.likkapet.data.apps.PackageManagerInstalledAppsSource
import com.likkapet.data.permissions.AndroidPermissionChecker
import com.likkapet.data.preferences.DataStoreStatsStore
import com.likkapet.data.preferences.LikkaDataStore
import com.likkapet.data.reaction.LocalReactionPhrases
import com.likkapet.data.remote.LikkaApiClient
import com.likkapet.data.roast.FallbackRoasts
import com.likkapet.data.roast.WorkerRoastGenerator
import com.likkapet.data.screen.BroadcastScreenStateSource
import com.likkapet.data.sensor.AndroidPostureSource
import com.likkapet.data.sensor.MotionSensorFeed
import com.likkapet.data.sensor.RateLoggingMotionFeed
import com.likkapet.data.sensor.SensorManagerMotionFeed
import com.likkapet.data.time.SystemWallClock
import com.likkapet.data.usage.AudioManagerCallStateReader
import com.likkapet.data.usage.DailyStatsRecorder
import com.likkapet.data.usage.ForegroundPackageTracker
import com.likkapet.data.usage.UsageStatsForegroundAppSource
import com.likkapet.data.usage.UsageStatsResumedEventsReader
import com.likkapet.domain.LocalReactionDisplay
import com.likkapet.domain.LocalReactionTracker
import com.likkapet.domain.OverlayMotionPlanner
import com.likkapet.domain.port.Clock
import com.likkapet.domain.port.ForegroundAppSource
import com.likkapet.domain.port.InstalledAppsSource
import com.likkapet.domain.port.MonitoringController
import com.likkapet.domain.port.PermissionChecker
import com.likkapet.domain.port.PostureSource
import com.likkapet.domain.port.ReactionPhrases
import com.likkapet.domain.port.RoastGenerator
import com.likkapet.domain.port.ScreenStateSource
import com.likkapet.domain.port.StatsStore
import com.likkapet.domain.port.WallClock
import com.likkapet.presentation.components.AppIconLoader
import com.likkapet.presentation.permissions.PermissionMonitor
import com.likkapet.service.MonitoringSession
import com.likkapet.service.ServiceMonitoringController
import com.likkapet.service.SessionPauseController
import com.likkapet.service.loggingExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.random.Random

/**
 * Manual dependency injection root (documentación §9.4). Callers only see `domain/port`
 * interfaces; the `data/` implementations are created here and nowhere else.
 */
class LikkaApplication : Application() {
    lateinit var monitoringController: MonitoringController
        private set
    lateinit var statsStore: StatsStore
        private set
    lateinit var installedAppsSource: InstalledAppsSource
        private set
    lateinit var permissionMonitor: PermissionMonitor
        private set
    lateinit var appIconLoader: AppIconLoader
        private set
    lateinit var roastGenerator: RoastGenerator
        private set
    lateinit var postureSource: PostureSource
        private set
    lateinit var foregroundAppSource: ForegroundAppSource
        private set
    lateinit var screenStateSource: ScreenStateSource
        private set
    lateinit var permissionChecker: PermissionChecker
        private set
    lateinit var pauseController: SessionPauseController
        private set
    val wallClock: WallClock = SystemWallClock

    // Monotonic clock for timers (documentación §9.5); the wall clock above is only for the calendar.
    private val monotonicClock = Clock { SystemClock.elapsedRealtime() }

    // Lives as long as the process: background roast requests and the stats writer outlive any screen.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + loggingExceptionHandler(SESSION_LOG_TAG))

    override fun onCreate() {
        super.onCreate()
        monitoringController = ServiceMonitoringController(this)
        statsStore = DataStoreStatsStore(LikkaDataStore.create(this), wallClock, ZoneId::systemDefault)
        installedAppsSource = PackageManagerInstalledAppsSource(this)
        permissionChecker = AndroidPermissionChecker(this)
        permissionMonitor = PermissionMonitor(permissionChecker)
        pauseController = SessionPauseController(statsStore)
        appIconLoader = PackageManagerAppIconLoader(this)::load
        createDataSources()
    }

    private fun createDataSources() {
        screenStateSource = BroadcastScreenStateSource(this, applicationScope)
        postureSource = AndroidPostureSource(motionFeed(), screenStateSource)
        foregroundAppSource = createForegroundAppSource()
        roastGenerator = createRoastGenerator()
    }

    /**
     * A fresh session for one run of the service: the stats recorder cannot be restarted once
     * stopped, so each run gets its own, and the coordinator starts from a clean state.
     */
    fun createMonitoringSession(scope: CoroutineScope) =
        MonitoringSession(
            scope = scope,
            clock = monotonicClock,
            wallClock = wallClock,
            zone = ZoneId::systemDefault,
            store = statsStore,
            postureSource = postureSource,
            foregroundAppSource = foregroundAppSource,
            screenStateSource = screenStateSource,
            roastGenerator = roastGenerator,
            recorder = DailyStatsRecorder(statsStore, monotonicClock, applicationScope, log = ::logSession),
            log = ::logSession,
        )

    /** The overlay's planner for one run of the service, on the same monotonic clock as the session. */
    fun createOverlayMotionPlanner() = OverlayMotionPlanner(monotonicClock, Random.Default)

    /** The overlay's local reactions (RF-O12) for one run of the service: phrases from assets, never the network. */
    fun createLocalReactionDisplay(resolvedLang: String = "es") =
        LocalReactionDisplay(
            monotonicClock,
            LocalReactionTracker(monotonicClock),
            if (resolvedLang.startsWith("en", ignoreCase = true)) englishReactionPhrases else spanishReactionPhrases,
        )

    private val spanishReactionPhrases: ReactionPhrases by lazy {
        LocalReactionPhrases(assets.open(LocalReactionPhrases.SPANISH_ASSET).bufferedReader().use { it.readText() })
    }

    private val englishReactionPhrases: ReactionPhrases by lazy {
        LocalReactionPhrases(assets.open(LocalReactionPhrases.ENGLISH_ASSET).bufferedReader().use { it.readText() })
    }

    /** Ends a session on the application scope, so the last minutes reach the store even if the service is already gone. */
    fun stopMonitoringSession(session: MonitoringSession) {
        applicationScope.launch { session.stop() }
    }

    /** Work that must outlive a broadcast receiver or a screen; the caller owns its own error handling. */
    fun launchInBackground(block: suspend () -> Unit) {
        applicationScope.launch { block() }
    }

    fun hasAllPermissions(): Boolean = permissionChecker.requiredPermissions.all(permissionChecker::isGranted)

    private fun logSession(message: String) = Log.i(SESSION_LOG_TAG, message)

    // The raw-rate log is a debug aid: it measures what the decimation hides (RNF-P02).
    private fun motionFeed(): MotionSensorFeed {
        val feed = SensorManagerMotionFeed(getSystemService(SensorManager::class.java))
        return if (BuildConfig.DEBUG) RateLoggingMotionFeed(feed) else feed
    }

    private fun createForegroundAppSource() =
        UsageStatsForegroundAppSource(
            tracker =
                ForegroundPackageTracker(
                    reader = UsageStatsResumedEventsReader(getSystemService(UsageStatsManager::class.java)),
                    wallClock = wallClock,
                ),
            callStateReader = AudioManagerCallStateReader(getSystemService(AudioManager::class.java)),
            screenState = screenStateSource,
            watchedPackages = statsStore.snapshot.map { it.settings.watchedPackages }.distinctUntilChanged(),
        )

    private fun createRoastGenerator(): RoastGenerator {
        val fallbackEs =
            FallbackRoasts(
                assets.open(FallbackRoasts.SPANISH_ASSET).bufferedReader().use { it.readText() },
            )
        val fallbackEn =
            FallbackRoasts(
                assets.open(FallbackRoasts.ENGLISH_ASSET).bufferedReader().use { it.readText() },
            )
        return WorkerRoastGenerator(
            api = LikkaApiClient(BuildConfig.LIKKA_WORKER_URL, BuildConfig.LIKKA_APP_TOKEN),
            fallback = fallbackEs,
            scope = applicationScope,
            clock = monotonicClock,
            wallClock = wallClock,
            zone = ZoneId::systemDefault,
            isAiEnabled = {
                statsStore.snapshot
                    .first()
                    .settings.aiEnabled
            },
            // Only the pool key (app enum, level, reason) and the result label are ever logged, in every
            // build, never a roast, a package name or the Worker's address.
            log = { message -> Log.i(ROAST_LOG_TAG, message) },
            currentLanguage = {
                statsStore.snapshot
                    .first()
                    .settings
                    .appLanguage
                    .resolve()
            },
            fallbackForLanguage = { lang ->
                if (lang.startsWith("en", ignoreCase = true)) fallbackEn else fallbackEs
            },
        )
    }

    private companion object {
        const val ROAST_LOG_TAG = "LikkaRoast"
        const val SESSION_LOG_TAG = "LikkaSession"
    }
}
