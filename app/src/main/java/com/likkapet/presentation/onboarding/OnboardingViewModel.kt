package com.likkapet.presentation.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likkapet.domain.model.AppPermission
import com.likkapet.domain.model.LikkaSettings
import com.likkapet.domain.model.MiuiTask
import com.likkapet.domain.port.MonitoringController
import com.likkapet.domain.port.StatsStore
import com.likkapet.presentation.permissions.PermissionMonitor
import com.likkapet.presentation.permissions.PermissionStatus
import com.likkapet.presentation.permissions.PermissionUi
import com.likkapet.presentation.persist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The six onboarding steps of design system §3.1; [MIUI] only appears on Xiaomi devices. */
enum class OnboardingStep { WELCOME, HOW_IT_WORKS, PRIVACY, PERMISSIONS, MIUI, DONE }

@Immutable
data class OnboardingUiState(
    val steps: List<OnboardingStep>,
    val currentIndex: Int,
    val aiEnabled: Boolean,
    val permissions: List<PermissionUi>,
    val miuiConfirmed: Set<MiuiTask>,
    // Set once onboarding is saved as completed; the screen then leaves for the dashboard.
    val isFinished: Boolean = false,
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
    settings: LikkaSettings,
    permissions: List<PermissionUi>,
    steps: List<OnboardingStep>,
    currentIndex: Int,
    isFinished: Boolean = false,
): OnboardingUiState =
    OnboardingUiState(
        steps = steps,
        currentIndex = currentIndex,
        aiEnabled = settings.aiEnabled,
        permissions = permissions,
        miuiConfirmed = settings.miuiConfirmed,
        isFinished = isFinished,
    )

class OnboardingViewModel(
    private val store: StatsStore,
    private val permissionMonitor: PermissionMonitor,
    private val monitoringController: MonitoringController,
    isMiui: Boolean,
) : ViewModel() {
    private val steps = stepsFor(isMiui)
    private val currentIndex = MutableStateFlow(0)
    private val isFinished = MutableStateFlow(false)

    // Defaults (not the stored values) until the first read: onboarding only runs on a fresh install.
    val uiState: StateFlow<OnboardingUiState> =
        combine(store.snapshot, permissionMonitor.permissions, currentIndex, isFinished) { snapshot, permissions, index, finished ->
            buildOnboardingUiState(snapshot.settings, permissions, steps, index, finished)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = buildOnboardingUiState(LikkaSettings(), permissionMonitor.permissions.value, steps, currentIndex.value),
        )

    fun onNextClick() = currentIndex.update { (it + 1).coerceAtMost(steps.lastIndex) }

    fun onBackClick() = currentIndex.update { (it - 1).coerceAtLeast(0) }

    fun onAiEnabledChange(enabled: Boolean) {
        persist { store.setAiEnabled(enabled) }
    }

    /** The screen opens the system screen or dialog; this only remembers the attempt. */
    fun onGrantPermissionClick(permission: AppPermission) = permissionMonitor.markRequested(permission)

    fun onPermissionResult() = permissionMonitor.refresh()

    fun onMiuiConfirmedChange(
        task: MiuiTask,
        confirmed: Boolean,
    ) {
        persist { store.setMiuiConfirmed(task, confirmed) }
    }

    /**
     * Starts the service right in the tap, while the app is surely in the foreground (Android 12+,
     * documentación §9.3), then saves onboarding as completed before leaving, so the next launch
     * never shows it again.
     */
    fun onFinishClick() {
        monitoringController.start()
        persist {
            store.completeOnboarding()
            isFinished.value = true
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
