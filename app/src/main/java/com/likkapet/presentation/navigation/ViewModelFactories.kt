package com.likkapet.presentation.navigation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.likkapet.domain.port.MonitoringController
import com.likkapet.presentation.dashboard.DashboardViewModel
import com.likkapet.presentation.onboarding.OnboardingStep
import com.likkapet.presentation.onboarding.OnboardingViewModel
import com.likkapet.presentation.permissions.PermissionsViewModel
import com.likkapet.presentation.privacy.PrivacyViewModel
import com.likkapet.presentation.settings.AboutViewModel
import com.likkapet.presentation.settings.SettingsViewModel
import com.likkapet.presentation.state.FakeAppStateStore

/** Manual ViewModel construction (no Hilt, documentación §9.4): one factory per screen. */
class ViewModelFactories(
    private val store: FakeAppStateStore,
    private val monitoringController: MonitoringController,
    private val isMiui: Boolean,
    private val versionName: String,
    private val readAsset: (String) -> String,
    private val initialOnboardingStep: OnboardingStep? = null,
) {
    fun onboarding(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer { OnboardingViewModel(store, monitoringController, isMiui, initialOnboardingStep) }
        }

    fun dashboard(): ViewModelProvider.Factory = viewModelFactory { initializer { DashboardViewModel(store, monitoringController) } }

    fun settings(): ViewModelProvider.Factory = viewModelFactory { initializer { SettingsViewModel(store, monitoringController) } }

    fun privacy(): ViewModelProvider.Factory = viewModelFactory { initializer { PrivacyViewModel(store) } }

    fun permissions(): ViewModelProvider.Factory = viewModelFactory { initializer { PermissionsViewModel(store) } }

    fun about(): ViewModelProvider.Factory = viewModelFactory { initializer { AboutViewModel(versionName, readAsset) } }
}
