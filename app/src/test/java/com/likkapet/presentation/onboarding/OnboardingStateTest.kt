package com.likkapet.presentation.onboarding

import com.likkapet.presentation.state.FakeAppState
import com.likkapet.presentation.state.PermissionStatus
import com.likkapet.presentation.state.pendingPermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {
    @Test
    fun `MIUI devices get six steps and the rest skip step five`() {
        assertEquals(OnboardingStep.entries, stepsFor(isMiui = true))
        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.HOW_IT_WORKS,
                OnboardingStep.PRIVACY,
                OnboardingStep.PERMISSIONS,
                OnboardingStep.DONE,
            ),
            stepsFor(isMiui = false),
        )
    }

    @Test
    fun `Xiaomi, Redmi and POCO count as MIUI whatever the casing`() {
        assertTrue(isMiuiDevice("Xiaomi", "Redmi"))
        assertTrue(isMiuiDevice("XIAOMI", "xiaomi"))
        assertTrue(isMiuiDevice("Unknown", "POCO"))
        assertFalse(isMiuiDevice("Google", "google"))
        assertFalse(isMiuiDevice("samsung", "samsung"))
    }

    @Test
    fun `the permissions step cannot be skipped until everything is granted`() {
        val pending = permissionsStepState(PermissionStatus.PENDING)
        val granted = permissionsStepState(PermissionStatus.GRANTED)

        assertFalse(pending.canContinue)
        assertTrue(granted.canContinue)
    }

    @Test
    fun `a denied permission keeps the step blocked and is reported`() {
        val denied = permissionsStepState(PermissionStatus.DENIED)

        assertFalse(denied.canContinue)
        assertTrue(denied.hasDeniedPermission)
    }

    @Test
    fun `other steps never block on permissions`() {
        val state = buildOnboardingUiState(FakeAppState(permissions = pendingPermissions(true)), stepsFor(true), currentIndex = 0)

        assertTrue(state.canContinue)
        assertTrue(state.isFirstStep)
    }

    private fun permissionsStepState(status: PermissionStatus): OnboardingUiState {
        val permissions = pendingPermissions(includeNotifications = true).mapValues { status }
        val steps = stepsFor(isMiui = false)
        return buildOnboardingUiState(FakeAppState(permissions = permissions), steps, steps.indexOf(OnboardingStep.PERMISSIONS))
    }
}
