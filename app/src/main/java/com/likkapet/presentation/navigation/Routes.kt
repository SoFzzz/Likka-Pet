package com.likkapet.presentation.navigation

import com.likkapet.presentation.state.FakeAppState

/** Destinations of the two navigation graphs (design system §3.2). */
object Routes {
    const val ONBOARDING_GRAPH = "onboarding_graph"
    const val ONBOARDING = "onboarding"

    const val MAIN_GRAPH = "main_graph"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val PRIVACY = "settings/privacy"
    const val PERMISSIONS = "settings/permissions"
    const val ABOUT = "settings/about"

    // Permissions screen shown on open when one was revoked from system settings (RF-A06).
    const val PERMISSIONS_GATE = "permissions_gate"
}

/** Where the app opens: which graph, and the first destination of the main graph. */
data class StartDestination(
    val graph: String,
    val mainStart: String = Routes.DASHBOARD,
)

/** Onboarding once; then the permissions gate if a permission is missing; otherwise the dashboard (§3.1). */
fun resolveStartDestination(state: FakeAppState): StartDestination =
    when {
        !state.onboardingCompleted -> StartDestination(Routes.ONBOARDING_GRAPH)
        !state.hasAllPermissions -> StartDestination(Routes.MAIN_GRAPH, Routes.PERMISSIONS_GATE)
        else -> StartDestination(Routes.MAIN_GRAPH)
    }
