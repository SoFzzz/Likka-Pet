package com.likkapet.presentation.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likkapet.domain.AppSearch
import com.likkapet.domain.WatchedApps
import com.likkapet.domain.model.InstalledApp
import com.likkapet.domain.model.LikkaSettings
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.ThemeMode
import com.likkapet.domain.port.InstalledAppsSource
import com.likkapet.domain.port.MonitoringController
import com.likkapet.domain.port.StatsStore
import com.likkapet.presentation.persist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class WatchedAppUi(
    val app: TargetApp,
    val isWatched: Boolean,
    // The last watched app still installed cannot be switched off (RF-S02).
    val canToggle: Boolean,
)

/** An app the user added (RF-S06), shown with its launcher label and icon. */
@Immutable
data class AddedAppUi(
    val packageName: String,
    val label: String,
    val isWatched: Boolean,
    val canToggle: Boolean,
    val canRemove: Boolean,
)

@Immutable
data class SettingsUiState(
    val isLikkaEnabled: Boolean,
    val isVibrationEnabled: Boolean,
    val themeMode: ThemeMode,
    val watchedApps: List<WatchedAppUi>,
    val addedApps: List<AddedAppUi>,
    val isAiEnabled: Boolean,
    val isDeactivateDialogVisible: Boolean,
) {
    companion object {
        fun preview() =
            buildSettingsUiState(
                settings = LikkaSettings(likkaEnabled = true, addedPackages = setOf(PREVIEW_PACKAGE)),
                launcherApps = listOf(InstalledApp(PREVIEW_PACKAGE, "Chrome"), InstalledApp("com.google.android.youtube", "YouTube")),
                isDeactivateDialogVisible = false,
            )

        private const val PREVIEW_PACKAGE = "com.android.chrome"
    }
}

/**
 * [launcherApps] is null while the installed apps are still being read: until then no watched app
 * can be switched off, since the "at least one installed" rule cannot be checked yet.
 */
fun buildSettingsUiState(
    settings: LikkaSettings,
    launcherApps: List<InstalledApp>?,
    isDeactivateDialogVisible: Boolean,
): SettingsUiState {
    val installed = launcherApps.orEmpty().map { it.packageName }.toSet()
    return SettingsUiState(
        isLikkaEnabled = settings.likkaEnabled,
        isVibrationEnabled = settings.vibrationEnabled,
        themeMode = settings.themeMode,
        watchedApps = TargetApp.DEFAULTS.map { defaultAppUi(it, settings.watchedPackages, installed) },
        addedApps = addedAppsUi(settings, launcherApps.orEmpty(), installed),
        isAiEnabled = settings.aiEnabled,
        isDeactivateDialogVisible = isDeactivateDialogVisible,
    )
}

private fun defaultAppUi(
    app: TargetApp,
    watched: Set<String>,
    installed: Set<String>,
): WatchedAppUi {
    val isWatched = WatchedApps.isWatched(app, watched)
    return WatchedAppUi(
        app,
        isWatched,
        canToggle =
            !isWatched || WatchedApps.canStopWatching(WatchedApps.packagesOf(app), watched, installed),
    )
}

// Added apps that are no longer installed stay stored but are not listed (they cannot be opened anyway).
private fun addedAppsUi(
    settings: LikkaSettings,
    launcherApps: List<InstalledApp>,
    installed: Set<String>,
): List<AddedAppUi> {
    val added = AppSearch.sortedByLabel(launcherApps.filter { it.packageName in settings.addedPackages })
    return added.map { app ->
        val isWatched = app.packageName in settings.watchedPackages
        val canLeave = !isWatched || WatchedApps.canStopWatching(setOf(app.packageName), settings.watchedPackages, installed)
        AddedAppUi(app.packageName, app.label, isWatched, canToggle = canLeave, canRemove = canLeave)
    }
}

class SettingsViewModel(
    private val store: StatsStore,
    private val installedAppsSource: InstalledAppsSource,
    private val monitoringController: MonitoringController,
) : ViewModel() {
    private val isDialogVisible = MutableStateFlow(false)
    private val launcherApps = MutableStateFlow<List<InstalledApp>?>(null)

    val uiState: StateFlow<SettingsUiState?> =
        combine(store.snapshot, launcherApps, isDialogVisible) { snapshot, apps, isDialog ->
            buildSettingsUiState(snapshot.settings, apps, isDialog)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null,
        )

    init {
        viewModelScope.launch { launcherApps.value = installedAppsSource.launcherApps() }
    }

    /** Switching on starts the service at once; switching off asks first (RF-S04). */
    fun onLikkaEnabledChange(enabled: Boolean) {
        if (enabled) {
            persist { store.setLikkaEnabled(true) }
            monitoringController.start()
        } else {
            isDialogVisible.update { true }
        }
    }

    fun onDeactivateConfirm() {
        persist { store.setLikkaEnabled(false) }
        monitoringController.stop()
        onDeactivateDismiss()
    }

    fun onDeactivateDismiss() = isDialogVisible.update { false }

    fun onVibrationChange(enabled: Boolean) {
        persist { store.setVibrationEnabled(enabled) }
    }

    fun onThemeSelected(mode: ThemeMode) {
        persist { store.setThemeMode(mode) }
    }

    fun onWatchedAppChange(
        app: TargetApp,
        watched: Boolean,
    ) {
        persist { store.setDefaultAppWatched(app, watched, installedPackages()) }
    }

    fun onAddedAppChange(
        packageName: String,
        watched: Boolean,
    ) {
        persist { store.setAddedAppWatched(packageName, watched, installedPackages()) }
    }

    fun onRemoveApp(packageName: String) {
        persist { store.removeApp(packageName, installedPackages()) }
    }

    fun onAiEnabledChange(enabled: Boolean) {
        persist { store.setAiEnabled(enabled) }
    }

    private fun installedPackages(): Set<String> =
        launcherApps.value
            .orEmpty()
            .map { it.packageName }
            .toSet()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
