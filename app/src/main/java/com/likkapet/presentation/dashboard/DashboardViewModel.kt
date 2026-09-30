package com.likkapet.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likkapet.domain.port.MonitoringController
import com.likkapet.presentation.state.FakeAppStateStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.ZoneId

/**
 * Dashboard state over the in-memory store. Activate / deactivate go through the real
 * [MonitoringController], so the foreground service really starts and stops.
 */
class DashboardViewModel(
    private val store: FakeAppStateStore,
    private val monitoringController: MonitoringController,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val local = MutableStateFlow(DashboardLocalState())

    // Re-evaluates "is the pause over" without any other change (the coordinator will own this).
    private val ticks =
        flow {
            while (true) {
                emit(nowMillis())
                delay(PAUSE_EXPIRY_CHECK_MS)
            }
        }

    val uiState: StateFlow<DashboardUiState> =
        combine(store.state, local, ticks) { state, localState, now ->
            buildDashboardUiState(state, localState, now, zone)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = buildDashboardUiState(store.state.value, local.value, nowMillis(), zone),
        )

    fun onPauseClick() = local.update { it.copy(isPauseSheetVisible = true) }

    fun onPauseSheetDismiss() = local.update { it.copy(isPauseSheetVisible = false) }

    fun onPauseOptionSelected(minutes: Int) = local.update { it.copy(selectedPauseMinutes = minutes) }

    fun onPauseConfirm() {
        // Rejected results (limit, level 3) also close the sheet: the dashboard button then
        // shows why the pause is unavailable.
        store.requestPause(local.value.selectedPauseMinutes, nowMillis())
        onPauseSheetDismiss()
    }

    fun onResumeClick() = store.resume()

    fun onActivateClick() {
        store.setLikkaEnabled(true)
        monitoringController.start()
    }

    fun onDeactivateClick() = local.update { it.copy(isDeactivateDialogVisible = true) }

    fun onDeactivateDismiss() = local.update { it.copy(isDeactivateDialogVisible = false) }

    fun onDeactivateConfirm() {
        store.setLikkaEnabled(false)
        monitoringController.stop()
        onDeactivateDismiss()
    }

    private companion object {
        const val PAUSE_EXPIRY_CHECK_MS = 15_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
