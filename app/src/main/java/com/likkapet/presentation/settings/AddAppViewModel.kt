package com.likkapet.presentation.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likkapet.domain.AppSearch
import com.likkapet.domain.model.InstalledApp
import com.likkapet.domain.port.InstalledAppsSource
import com.likkapet.domain.port.StatsStore
import com.likkapet.presentation.persist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class AddableAppUi(
    val packageName: String,
    val label: String,
    val isAdded: Boolean,
)

/** The three states of "Añadir app" (design system §2.5). */
sealed interface AddAppListState {
    data object Loading : AddAppListState

    /** [isEverythingAdded]: no app is left to add ("Ya añadiste todas las apps que puedo ver."). */
    @Immutable
    data class Results(
        val apps: List<AddableAppUi>,
        val isEverythingAdded: Boolean,
    ) : AddAppListState

    data object NoResults : AddAppListState
}

@Immutable
data class AddAppUiState(
    val query: String,
    val list: AddAppListState,
)

/** [addableApps] null = still loading; already sorted and without the default apps. */
fun buildAddAppUiState(
    addableApps: List<InstalledApp>?,
    addedPackages: Set<String>,
    query: String,
): AddAppUiState {
    if (addableApps == null) return AddAppUiState(query, AddAppListState.Loading)
    val matches = AppSearch.filter(addableApps, query)
    val list =
        if (matches.isEmpty() && query.isNotBlank()) {
            AddAppListState.NoResults
        } else {
            AddAppListState.Results(
                apps = matches.map { AddableAppUi(it.packageName, it.label, it.packageName in addedPackages) },
                isEverythingAdded = query.isBlank() && addableApps.all { it.packageName in addedPackages },
            )
        }
    return AddAppUiState(query, list)
}

class AddAppViewModel(
    private val store: StatsStore,
    private val installedAppsSource: InstalledAppsSource,
) : ViewModel() {
    private val addableApps = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<AddAppUiState> =
        combine(addableApps, store.snapshot.map { it.settings.addedPackages }, query, ::buildAddAppUiState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = AddAppUiState("", AddAppListState.Loading),
            )

    init {
        viewModelScope.launch { addableApps.value = AppSearch.addableApps(installedAppsSource.launcherApps()) }
    }

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun onClearQuery() = onQueryChange("")

    /** Added switched on, and the screen stays open to add more (design system §2.5). */
    fun onAddClick(packageName: String) {
        persist { store.addApp(packageName) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
