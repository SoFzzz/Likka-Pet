package com.likkapet.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.model.AiServiceStatus
import com.likkapet.domain.port.MonitoringController
import com.likkapet.domain.port.PauseController
import com.likkapet.domain.port.StatsStore
import com.likkapet.domain.port.WallClock
import com.likkapet.presentation.permissions.PermissionMonitor
import com.likkapet.presentation.permissions.PermissionStatus
import com.likkapet.presentation.persist
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

/**
 * Dashboard over the persisted state (RF-D03, RF-D05). Activate / deactivate go through the real
 * [MonitoringController], so the foreground service really starts and stops. [uiState] is null
 * until the store has been read once, so no default value ever flashes on screen.
 */
class DashboardViewModel(
    private val store: StatsStore,
    private val permissionMonitor: PermissionMonitor,
    private val monitoringController: MonitoringController,
    private val pauseController: PauseController,
    private val wallClock: WallClock,
    private val zone: ZoneId,
    private val aiServiceStatus: Flow<AiServiceStatus>,
) : ViewModel() {
    private val local = MutableStateFlow(DashboardLocalState())

    // Re-evaluates "is the pause over" against the stored `paused_until` without any other change.
    private val ticks =
        flow {
            while (true) {
                emit(wallClock.nowEpochMillis())
                delay(EscalationConfig.PAUSE_EXPIRY_CHECK_SEC.seconds)
            }
        }

    private val runtimeSignals: Flow<RuntimeSignals> =
        combine(aiServiceStatus, pauseController.isLevel3Active) { aiStatus, isLevel3Active ->
            RuntimeSignals(isLevel3Active = isLevel3Active, isOnline = aiStatus.isOnline, hasAiCredit = aiStatus.hasCredit)
        }

    val uiState: StateFlow<DashboardUiState?> =
        combine(
            store.snapshot,
            permissionMonitor.permissions,
            local,
            ticks,
            runtimeSignals,
        ) { snapshot, permissions, localState, now, live ->
            val hasAllPermissions = permissions.all { it.status == PermissionStatus.GRANTED }
            buildDashboardUiState(snapshot, hasAllPermissions, live, localState, now, zone)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null,
        )

    fun onPauseClick() = local.update { it.copy(isPauseSheetVisible = true) }

    fun onPauseSheetDismiss() = local.update { it.copy(isPauseSheetVisible = false) }

    fun onPauseOptionSelected(minutes: Int) = local.update { it.copy(selectedPauseMinutes = minutes) }

    fun onPauseConfirm() {
        // Rejected results (limit, level 3) also close the sheet: the dashboard button then
        // shows why the pause is unavailable.
        val minutes = local.value.selectedPauseMinutes
        persist { pauseController.requestPause(minutes) }
        onPauseSheetDismiss()
    }

    fun onResumeClick() {
        persist { pauseController.resume() }
    }

    fun onActivateClick() {
        persist { store.setLikkaEnabled(true) }
        monitoringController.start()
    }

    fun onDeactivateClick() = local.update { it.copy(isDeactivateDialogVisible = true) }

    fun onDeactivateDismiss() = local.update { it.copy(isDeactivateDialogVisible = false) }

    fun onDeactivateConfirm() {
        persist { store.setLikkaEnabled(false) }
        monitoringController.stop()
        onDeactivateDismiss()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
