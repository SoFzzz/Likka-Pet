package com.likkapet.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the app opens (design system §3.1, RF-A06). */
class StartDestinationTest {
    @Test
    fun `first launch opens onboarding`() {
        assertEquals(Routes.ONBOARDING_GRAPH, resolveStartDestination(isOnboardingCompleted = false, hasAllPermissions = false).graph)
    }

    @Test
    fun `a finished onboarding with every permission opens the dashboard`() {
        val start = resolveStartDestination(isOnboardingCompleted = true, hasAllPermissions = true)

        assertEquals(StartDestination(Routes.MAIN_GRAPH, Routes.DASHBOARD), start)
    }

    @Test
    fun `a revoked permission opens the permissions screen, not onboarding`() {
        val start = resolveStartDestination(isOnboardingCompleted = true, hasAllPermissions = false)

        assertEquals(StartDestination(Routes.MAIN_GRAPH, Routes.PERMISSIONS_GATE), start)
    }
}
