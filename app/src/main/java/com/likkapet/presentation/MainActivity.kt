package com.likkapet.presentation

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.likkapet.LikkaApplication
import com.likkapet.presentation.navigation.LikkaNavHost
import com.likkapet.presentation.navigation.Routes
import com.likkapet.presentation.navigation.StartDestination
import com.likkapet.presentation.navigation.ViewModelFactories
import com.likkapet.presentation.navigation.resolveStartDestination
import com.likkapet.presentation.onboarding.isMiuiDevice
import com.likkapet.presentation.theme.LikkaTheme
import com.likkapet.presentation.theme.LocalLikkaIsLightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LikkaApplication
        val store = app.appStateStore
        val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val debug = DebugLaunchOptions.from(intent, isDebuggable)
        if (savedInstanceState == null) debug.applyTo(store, System.currentTimeMillis())
        val factories =
            ViewModelFactories(
                store = store,
                monitoringController = app.monitoringController,
                isMiui = isMiuiDevice(Build.MANUFACTURER, Build.BRAND) || debug.forceMiui,
                versionName = readVersionName(),
                readAsset = ::readAsset,
                initialOnboardingStep = debug.onboardingStep,
            )
        // A forced dashboard state must show the dashboard, not the revoked-permission gate.
        val start =
            if (debug.dashboardStatus != null) StartDestination(Routes.MAIN_GRAPH) else resolveStartDestination(store.state.value)
        setContent {
            val state by store.state.collectAsStateWithLifecycle()
            LikkaTheme(state.themeMode) {
                SystemBarIcons()
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    LikkaNavHost(factories = factories, start = start, navController = navController)
                    if (savedInstanceState == null) {
                        debug.route?.let { route -> LaunchedEffect(route) { navController.navigate(route) } }
                    }
                }
            }
        }
    }

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
