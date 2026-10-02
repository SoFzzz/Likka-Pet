package com.likkapet.presentation.navigation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.likkapet.domain.model.AiServiceStatus
import com.likkapet.domain.port.InstalledAppsSource
import com.likkapet.domain.port.MonitoringController
import com.likkapet.domain.port.PauseController
import com.likkapet.domain.port.StatsStore
import com.likkapet.domain.port.WallClock
import com.likkapet.presentation.components.AppIconLoader
import com.likkapet.presentation.dashboard.DashboardViewModel
import com.likkapet.presentation.onboarding.OnboardingViewModel
import com.likkapet.presentation.permissions.PermissionMonitor
import com.likkapet.presentation.permissions.PermissionsViewModel
import com.likkapet.presentation.privacy.PrivacyViewModel
import com.likkapet.presentation.settings.AboutViewModel
import com.likkapet.presentation.settings.AddAppViewModel
import com.likkapet.presentation.settings.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

/** Manual ViewModel construction (no Hilt, documentación §9.4): one factory per screen. */
class ViewModelFactories(
    private val store: StatsStore,
    private val permissionMonitor: PermissionMonitor,
    private val monitoringController: MonitoringController,
    private val pauseController: PauseController,
    private val installedAppsSource: InstalledAppsSource,
    private val wallClock: WallClock,
    private val zone: ZoneId,
    private val aiServiceStatus: Flow<AiServiceStatus>,
    private val isMiui: Boolean,
    private val versionName: String,
    private val readAsset: (String) -> String,
    val iconLoader: AppIconLoader,
) {
    fun onboarding(): ViewModelProvider.Factory =
        viewModelFactory { initializer { OnboardingViewModel(store, permissionMonitor, monitoringController, isMiui) } }

    fun dashboard(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                DashboardViewModel(
                    store,
                    permissionMonitor,
                    monitoringController,
                    pauseController,
                    wallClock,
                    zone,
                    aiServiceStatus,
                )
            }
        }

    fun settings(): ViewModelProvider.Factory =
        viewModelFactory { initializer { SettingsViewModel(store, installedAppsSource, monitoringController) } }

    fun addApp(): ViewModelProvider.Factory = viewModelFactory { initializer { AddAppViewModel(store, installedAppsSource) } }

    fun privacy(): ViewModelProvider.Factory = viewModelFactory { initializer { PrivacyViewModel(store) } }

    fun permissions(): ViewModelProvider.Factory = viewModelFactory { initializer { PermissionsViewModel(permissionMonitor) } }

    fun about(): ViewModelProvider.Factory = viewModelFactory { initializer { AboutViewModel(versionName, readAsset) } }
}
