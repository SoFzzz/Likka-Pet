package com.likkapet.presentation.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.ThemeMode
import com.likkapet.domain.port.MonitoringController
import com.likkapet.presentation.state.FakeAppState
import com.likkapet.presentation.state.FakeAppStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@Immutable
data class WatchedAppUi(
    val app: TargetApp,
    val isWatched: Boolean,
    // The last watched app cannot be switched off (RF-S02).
    val canToggle: Boolean,
)

@Immutable
data class SettingsUiState(
    val isLikkaEnabled: Boolean,
    val isVibrationEnabled: Boolean,
    val themeMode: ThemeMode,
    val watchedApps: List<WatchedAppUi>,
    val isAiEnabled: Boolean,
    val isDeactivateDialogVisible: Boolean,
) {
    companion object {
        fun preview() = buildSettingsUiState(FakeAppState(likkaEnabled = true), isDeactivateDialogVisible = false)
    }
}

fun buildSettingsUiState(
    state: FakeAppState,
    isDeactivateDialogVisible: Boolean,
): SettingsUiState =
    SettingsUiState(
        isLikkaEnabled = state.likkaEnabled,
        isVibrationEnabled = state.vibrationEnabled,
        themeMode = state.themeMode,
        watchedApps =
            TargetApp.entries.map { app ->
                val isWatched = app in state.watchedApps
                WatchedAppUi(app, isWatched, canToggle = !isWatched || state.watchedApps.size > 1)
            },
        isAiEnabled = state.aiEnabled,
        isDeactivateDialogVisible = isDeactivateDialogVisible,
    )

class SettingsViewModel(
    private val store: FakeAppStateStore,
    private val monitoringController: MonitoringController,
) : ViewModel() {
    private val isDialogVisible = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> =
        combine(store.state, isDialogVisible, ::buildSettingsUiState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = buildSettingsUiState(store.state.value, isDialogVisible.value),
            )

    /** Switching on starts the service at once; switching off asks first (RF-S04). */
    fun onLikkaEnabledChange(enabled: Boolean) {
        if (enabled) {
            store.setLikkaEnabled(true)
            monitoringController.start()
        } else {
            isDialogVisible.update { true }
        }
    }

    fun onDeactivateConfirm() {
        store.setLikkaEnabled(false)
        monitoringController.stop()
        onDeactivateDismiss()
    }

    fun onDeactivateDismiss() = isDialogVisible.update { false }

    fun onVibrationChange(enabled: Boolean) = store.setVibrationEnabled(enabled)

    fun onThemeSelected(mode: ThemeMode) = store.setThemeMode(mode)

    fun onWatchedAppChange(
        app: TargetApp,
        watched: Boolean,
    ) = store.setWatched(app, watched)

    fun onAiEnabledChange(enabled: Boolean) = store.setAiEnabled(enabled)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
