package com.likkapet.presentation.navigation

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
    const val ADD_APP = "settings/add_app"

    // Permissions screen shown on open when one was revoked from system settings (RF-A06).
    const val PERMISSIONS_GATE = "permissions_gate"
}

/** Where the app opens: which graph, and the first destination of the main graph. */
data class StartDestination(
    val graph: String,
    val mainStart: String = Routes.DASHBOARD,
)

/** Onboarding once; then the permissions gate if a permission is missing; otherwise the dashboard (§3.1). */
fun resolveStartDestination(
    isOnboardingCompleted: Boolean,
    hasAllPermissions: Boolean,
): StartDestination =
    when {
        !isOnboardingCompleted -> StartDestination(Routes.ONBOARDING_GRAPH)
        !hasAllPermissions -> StartDestination(Routes.MAIN_GRAPH, Routes.PERMISSIONS_GATE)
        else -> StartDestination(Routes.MAIN_GRAPH)
    }

/**
 * Whether opening the app should (re)start the service, e.g. after a force-stop: only once
 * onboarding is done, Likka is switched on and every required permission is granted.
 */
fun shouldStartMonitoring(
    isOnboardingCompleted: Boolean,
    isLikkaEnabled: Boolean,
    hasAllPermissions: Boolean,
): Boolean = isOnboardingCompleted && isLikkaEnabled && hasAllPermissions
