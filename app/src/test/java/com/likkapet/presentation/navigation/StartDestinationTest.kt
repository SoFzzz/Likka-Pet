package com.likkapet.presentation.navigation

import com.likkapet.presentation.state.AppPermission
import com.likkapet.presentation.state.FakeAppState
import com.likkapet.presentation.state.PermissionStatus
import com.likkapet.presentation.state.pendingPermissions
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the app opens (design system §3.1, RF-A06). */
class StartDestinationTest {
    private val granted = pendingPermissions(includeNotifications = true).mapValues { PermissionStatus.GRANTED }

    @Test
    fun `first launch opens onboarding`() {
        assertEquals(Routes.ONBOARDING_GRAPH, resolveStartDestination(FakeAppState()).graph)
    }

    @Test
    fun `a finished onboarding with every permission opens the dashboard`() {
        val start = resolveStartDestination(FakeAppState(onboardingCompleted = true, permissions = granted))

        assertEquals(StartDestination(Routes.MAIN_GRAPH, Routes.DASHBOARD), start)
    }

    @Test
    fun `a revoked permission opens the permissions screen, not onboarding`() {
        val revoked = granted + (AppPermission.OVERLAY to PermissionStatus.DENIED)

        val start = resolveStartDestination(FakeAppState(onboardingCompleted = true, permissions = revoked))

        assertEquals(StartDestination(Routes.MAIN_GRAPH, Routes.PERMISSIONS_GATE), start)
    }
}
