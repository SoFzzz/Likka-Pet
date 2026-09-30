package com.likkapet.presentation.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.likkapet.R
import com.likkapet.presentation.components.LikkaPreview
import com.likkapet.presentation.components.LikkaThemePreviews
import com.likkapet.presentation.components.LikkaTopBar
import com.likkapet.presentation.components.screenInsets
import com.likkapet.presentation.state.FakeAppStateStore
import com.likkapet.presentation.theme.LikkaSpacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PrivacyViewModel(
    private val store: FakeAppStateStore,
) : ViewModel() {
    val isAiEnabled: StateFlow<Boolean> =
        store.state
            .map { it.aiEnabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), store.state.value.aiEnabled)

    fun onAiEnabledChange(enabled: Boolean) = store.setAiEnabled(enabled)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Settings › "Qué datos se envían": the same explanation as onboarding step 3 (design system §3.1). */
@Composable
fun PrivacyScreen(
    viewModel: PrivacyViewModel,
    onBackClick: () -> Unit,
) {
    val isAiEnabled by viewModel.isAiEnabled.collectAsStateWithLifecycle()
    PrivacyContent(isAiEnabled = isAiEnabled, onAiEnabledChange = viewModel::onAiEnabledChange, onBackClick = onBackClick)
}

@Composable
fun PrivacyContent(
    isAiEnabled: Boolean,
    onAiEnabledChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.screenInsets()) {
        LikkaTopBar(title = stringResource(R.string.privacy_title), onBackClick = onBackClick)
        PrivacyBody(
            aiEnabled = isAiEnabled,
            onAiEnabledChange = onAiEnabledChange,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LikkaSpacing.l),
        )
    }
}

@LikkaThemePreviews
@Composable
private fun PrivacyContentPreview() {
    LikkaPreview { PrivacyContent(isAiEnabled = true, onAiEnabledChange = {}, onBackClick = {}) }
}
