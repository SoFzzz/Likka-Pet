package com.likkapet.presentation

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.likkapet.LikkaApplication
import com.likkapet.domain.model.LikkaSnapshot
import com.likkapet.presentation.navigation.LikkaNavHost
import com.likkapet.presentation.navigation.StartDestination
import com.likkapet.presentation.navigation.ViewModelFactories
import com.likkapet.presentation.navigation.resolveStartDestination
import com.likkapet.presentation.navigation.shouldStartMonitoring
import com.likkapet.presentation.onboarding.isMiuiDevice
import com.likkapet.presentation.theme.LikkaTheme
import com.likkapet.presentation.theme.LocalLikkaIsLightTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * The only activity, declared `singleTask` in the manifest: launching it again (launcher, the
 * notification, `am start`) reuses this instance instead of stacking another dashboard on top.
 */
class MainActivity : ComponentActivity() {
    private val app: LikkaApplication get() = application as LikkaApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The process may have outlived a permission revoked in system settings.
        app.permissionMonitor.refresh()
        val factories = createFactories()
        setContent {
            // Only the window background shows until the store has been read once (milliseconds).
            val snapshot by app.statsStore.snapshot.collectAsStateWithLifecycle(initialValue = null)
            snapshot?.let { AppContent(it, factories) }
        }
    }

    /** Re-reads permissions after returning from system settings (RF-A06) and restarts the service if needed. */
    override fun onResume() {
        super.onResume()
        app.permissionMonitor.refresh()
        lifecycleScope.launch {
            app.statsStore.refreshDay()
            val settings =
                app.statsStore.snapshot
                    .first()
                    .settings
            val shouldStart =
                shouldStartMonitoring(settings.onboardingCompleted, settings.likkaEnabled, app.permissionMonitor.hasAllPermissions)
            // Android 12+ only lets a foreground app start a foreground service (documentación §9.3).
            if (shouldStart && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) app.monitoringController.start()
        }
    }

    @Composable
    private fun AppContent(
        snapshot: LikkaSnapshot,
        factories: ViewModelFactories,
    ) {
        // Decided once per activity (and kept across recreation): later changes navigate instead.
        val initial = resolveStartDestination(snapshot.settings.onboardingCompleted, app.permissionMonitor.hasAllPermissions)
        val startGraph = rememberSaveable { initial.graph }
        val mainStart = rememberSaveable { initial.mainStart }
        LikkaTheme(snapshot.settings.themeMode) {
            SystemBarIcons()
            Surface(color = MaterialTheme.colorScheme.background) {
                LikkaNavHost(
                    factories = factories,
                    start = StartDestination(startGraph, mainStart),
                    navController = rememberNavController(),
                )
            }
        }
    }

    private fun createFactories() =
        ViewModelFactories(
            store = app.statsStore,
            permissionMonitor = app.permissionMonitor,
            monitoringController = app.monitoringController,
            installedAppsSource = app.installedAppsSource,
            wallClock = app.wallClock,
            zone = ZoneId.systemDefault(),
            isMiui = isMiuiDevice(Build.MANUFACTURER, Build.BRAND),
            versionName = readVersionName(),
            readAsset = ::readAsset,
            iconLoader = app.appIconLoader,
        )

    private fun readVersionName(): String {
        val info =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
        return info.versionName.orEmpty()
    }

    private fun readAsset(path: String): String = assets.open(path).bufferedReader().use { it.readText() }
}

/** Dark system-bar icons on the light theme, light ones on the dark theme (design system §1.1, regla 4). */
@Composable
private fun SystemBarIcons() {
    val isLight = LocalLikkaIsLightTheme.current
    val view = LocalView.current
    SideEffect {
        val window = (view.context as ComponentActivity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = isLight
    }
}
