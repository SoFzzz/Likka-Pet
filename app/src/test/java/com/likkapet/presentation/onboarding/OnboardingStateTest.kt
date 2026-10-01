package com.likkapet.presentation.onboarding

import com.likkapet.domain.model.AppPermission
import com.likkapet.domain.model.LikkaSettings
import com.likkapet.presentation.permissions.PermissionStatus
import com.likkapet.presentation.permissions.PermissionUi
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
        assertFalse(permissionsStepState(PermissionStatus.PENDING).canContinue)
        assertTrue(permissionsStepState(PermissionStatus.GRANTED).canContinue)
    }

    @Test
    fun `below API 33 the two permissions are enough`() {
        val twoGranted = listOf(AppPermission.OVERLAY, AppPermission.USAGE_STATS).map { PermissionUi(it, PermissionStatus.GRANTED) }
        val steps = stepsFor(isMiui = true)

        val state = buildOnboardingUiState(LikkaSettings(), twoGranted, steps, steps.indexOf(OnboardingStep.PERMISSIONS))

        assertTrue(state.canContinue)
    }

    @Test
    fun `a denied permission keeps the step blocked and is reported`() {
        val denied = permissionsStepState(PermissionStatus.DENIED)

        assertFalse(denied.canContinue)
        assertTrue(denied.hasDeniedPermission)
    }

    @Test
    fun `other steps never block on permissions`() {
        val pending = AppPermission.entries.map { PermissionUi(it, PermissionStatus.PENDING) }

        val state = buildOnboardingUiState(LikkaSettings(), pending, stepsFor(true), currentIndex = 0)

        assertTrue(state.canContinue)
        assertTrue(state.isFirstStep)
    }

    @Test
    fun `the AI switch and MIUI checks come from the stored settings`() {
        val settings = LikkaSettings(aiEnabled = false)

        val state = buildOnboardingUiState(settings, emptyList(), stepsFor(true), currentIndex = 0)

        assertFalse(state.aiEnabled)
        assertTrue(state.miuiConfirmed.isEmpty())
    }

    private fun permissionsStepState(status: PermissionStatus): OnboardingUiState {
        val permissions = AppPermission.entries.map { PermissionUi(it, status) }
        val steps = stepsFor(isMiui = false)
        return buildOnboardingUiState(LikkaSettings(), permissions, steps, steps.indexOf(OnboardingStep.PERMISSIONS))
    }
}
