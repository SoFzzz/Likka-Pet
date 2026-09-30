package com.likkapet.presentation

import android.content.Intent
import com.likkapet.domain.model.ThemeMode
import com.likkapet.presentation.dashboard.DashboardStatus
import com.likkapet.presentation.navigation.Routes
import com.likkapet.presentation.onboarding.OnboardingStep
import com.likkapet.presentation.state.AppPermission
import com.likkapet.presentation.state.FakeAppStateStore
import com.likkapet.presentation.state.PermissionStatus

/**
 * Launch extras that force a fake state so every screen can be captured on an emulator, e.g.
 * `adb shell am start -n com.likkapet/.presentation.MainActivity --es debug_dashboard_state PAUSED`.
 * Read only when the app is debuggable ([from] returns [NONE] otherwise), and only on a fresh
 * launch. Scaffolding for the fake-state phase: delete it when `data/` provides real state.
 */
data class DebugLaunchOptions(
    val themeMode: ThemeMode? = null,
    val dashboardStatus: DashboardStatus? = null,
    val onboardingStep: OnboardingStep? = null,
    val forceMiui: Boolean = false,
    val route: String? = null,
) {
    /** Pushes the requested fake state into [store]; call before resolving the start destination. */
    fun applyTo(
        store: FakeAppStateStore,
        nowMillis: Long,
    ) {
        themeMode?.let(store::setThemeMode)
        dashboardStatus?.let { applyDashboardStatus(store, it, nowMillis) }
    }

    private fun applyDashboardStatus(
        store: FakeAppStateStore,
        status: DashboardStatus,
        nowMillis: Long,
    ) {
        store.update { state ->
            val allGranted = state.permissions.mapValues { PermissionStatus.GRANTED }
            val base = state.copy(onboardingCompleted = true, likkaEnabled = true, permissions = allGranted)
            when (status) {
                DashboardStatus.PROTECTING -> {
                    base
                }

                DashboardStatus.PAUSED -> {
                    base.copy(pausedUntilMillis = nowMillis + DEBUG_PAUSE_MILLIS, pausesToday = 1)
                }

                DashboardStatus.DISABLED -> {
                    base.copy(likkaEnabled = false)
                }

                DashboardStatus.PERMISSION_MISSING -> {
                    base.copy(permissions = allGranted + (AppPermission.OVERLAY to PermissionStatus.DENIED))
                }

                DashboardStatus.OFFLINE -> {
                    base.copy(isOnline = false)
                }

                DashboardStatus.AI_DISABLED -> {
                    base.copy(aiEnabled = false)
                }
            }
        }
    }

    companion object {
        val NONE = DebugLaunchOptions()

        private const val DEBUG_PAUSE_MILLIS = 30 * 60_000L

        // Route extras name the settings subscreens; anything else is ignored.
        private val ROUTES =
            mapOf(
                "settings" to Routes.SETTINGS,
                "privacy" to Routes.PRIVACY,
                "permissions" to Routes.PERMISSIONS,
                "about" to Routes.ABOUT,
            )

        fun from(
            intent: Intent,
            isDebuggable: Boolean,
        ): DebugLaunchOptions {
            if (!isDebuggable) return NONE
            return DebugLaunchOptions(
                themeMode = intent.enumExtra<ThemeMode>("debug_theme"),
                dashboardStatus = intent.enumExtra<DashboardStatus>("debug_dashboard_state"),
                onboardingStep = intent.enumExtra<OnboardingStep>("debug_onboarding_step"),
                forceMiui = intent.getBooleanExtra("debug_force_miui", false),
                route = ROUTES[intent.getStringExtra("debug_route")],
            )
        }

        private inline fun <reified E : Enum<E>> Intent.enumExtra(name: String): E? =
            getStringExtra(name)?.let { value -> enumValues<E>().firstOrNull { it.name == value.uppercase() } }
    }
}
