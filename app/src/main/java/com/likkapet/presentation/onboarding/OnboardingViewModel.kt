package com.likkapet.presentation.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likkapet.domain.port.MonitoringController
import com.likkapet.presentation.permissions.PermissionUi
import com.likkapet.presentation.state.AppPermission
import com.likkapet.presentation.state.FakeAppState
import com.likkapet.presentation.state.FakeAppStateStore
import com.likkapet.presentation.state.MiuiTask
import com.likkapet.presentation.state.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** The six onboarding steps of design system §3.1; [MIUI] only appears on Xiaomi devices. */
enum class OnboardingStep { WELCOME, HOW_IT_WORKS, PRIVACY, PERMISSIONS, MIUI, DONE }

@Immutable
data class OnboardingUiState(
    val steps: List<OnboardingStep>,
    val currentIndex: Int,
    val aiEnabled: Boolean,
    val permissions: List<PermissionUi>,
    val miuiConfirmed: Set<MiuiTask>,
) {
    val step: OnboardingStep get() = steps[currentIndex]
    val isFirstStep: Boolean get() = currentIndex == 0
    val hasAllPermissions: Boolean get() = permissions.all { it.status == PermissionStatus.GRANTED }
    val hasDeniedPermission: Boolean get() = permissions.any { it.status == PermissionStatus.DENIED }

    /** The permissions step cannot be skipped (design system §3.2). */
    val canContinue: Boolean get() = step != OnboardingStep.PERMISSIONS || hasAllPermissions

    companion object {
        fun preview(
            step: OnboardingStep = OnboardingStep.WELCOME,
            isMiui: Boolean = true,
            permissionStatus: PermissionStatus = PermissionStatus.PENDING,
        ): OnboardingUiState {
            val steps = stepsFor(isMiui)
            return OnboardingUiState(
                steps = steps,
                currentIndex = steps.indexOf(step).coerceAtLeast(0),
                aiEnabled = true,
                permissions = AppPermission.entries.map { PermissionUi(it, permissionStatus) },
                miuiConfirmed = emptySet(),
            )
        }
    }
}

/** Step 5 is skipped on non-MIUI devices (RF-A05). */
fun stepsFor(isMiui: Boolean): List<OnboardingStep> = OnboardingStep.entries.filter { isMiui || it != OnboardingStep.MIUI }

fun buildOnboardingUiState(
    state: FakeAppState,
    steps: List<OnboardingStep>,
    currentIndex: Int,
): OnboardingUiState =
    OnboardingUiState(
        steps = steps,
        currentIndex = currentIndex,
        aiEnabled = state.aiEnabled,
        permissions = state.permissions.map { (permission, status) -> PermissionUi(permission, status) },
        miuiConfirmed = state.miuiConfirmed,
    )

class OnboardingViewModel(
    private val store: FakeAppStateStore,
    private val monitoringController: MonitoringController,
    isMiui: Boolean,
    initialStep: OnboardingStep? = null,
) : ViewModel() {
    private val steps = stepsFor(isMiui)
    private val currentIndex = MutableStateFlow(initialStep?.let { steps.indexOf(it) }?.coerceAtLeast(0) ?: 0)

    val uiState: StateFlow<OnboardingUiState> =
        combine(store.state, currentIndex) { state, index -> buildOnboardingUiState(state, steps, index) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = buildOnboardingUiState(store.state.value, steps, currentIndex.value),
            )

    fun onNextClick() = currentIndex.update { (it + 1).coerceAtMost(steps.lastIndex) }

    fun onBackClick() = currentIndex.update { (it - 1).coerceAtLeast(0) }

    fun onAiEnabledChange(enabled: Boolean) = store.setAiEnabled(enabled)

    fun onGrantPermissionClick(permission: AppPermission) = store.grantPermission(permission)

    fun onMiuiConfirmedChange(
        task: MiuiTask,
        confirmed: Boolean,
    ) = store.setMiuiConfirmed(task, confirmed)

    /** Ends onboarding and starts the service from the foreground (documentación §9.3). */
    fun onFinishClick() {
        store.completeOnboarding()
        monitoringController.start()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
