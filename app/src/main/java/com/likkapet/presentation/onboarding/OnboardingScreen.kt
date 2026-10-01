package com.likkapet.presentation.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.likkapet.R
import com.likkapet.domain.model.AppPermission
import com.likkapet.domain.model.MiuiTask
import com.likkapet.presentation.components.LikkaButton
import com.likkapet.presentation.components.LikkaButtonVariant
import com.likkapet.presentation.components.LikkaPreview
import com.likkapet.presentation.components.LikkaThemePreviews
import com.likkapet.presentation.components.StepIndicator
import com.likkapet.presentation.components.screenInsets
import com.likkapet.presentation.permissions.PermissionStatus
import com.likkapet.presentation.system.rememberSystemActions
import com.likkapet.presentation.theme.LikkaSpacing

/** Everything the onboarding can ask for; the screen forwards them, no logic in the composable. */
data class OnboardingActions(
    val onNextClick: () -> Unit = {},
    val onBackClick: () -> Unit = {},
    val onAiEnabledChange: (Boolean) -> Unit = {},
    val onGrantPermissionClick: (AppPermission) -> Unit = {},
    val onMiuiConfirmedChange: (MiuiTask, Boolean) -> Unit = { _, _ -> },
    val onOpenMiuiSettingsClick: (MiuiTask) -> Unit = {},
    val onFinishClick: () -> Unit = {},
)

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val systemActions = rememberSystemActions(onPermissionResult = viewModel::onPermissionResult)
    LaunchedEffect(state.isFinished) { if (state.isFinished) onFinished() }
    // Step 1 leaves the app (system back); steps 2–6 go to the previous step (design system §3.3).
    BackHandler(enabled = !state.isFirstStep, onBack = viewModel::onBackClick)
    OnboardingContent(
        state = state,
        actions =
            OnboardingActions(
                onNextClick = viewModel::onNextClick,
                onBackClick = viewModel::onBackClick,
                onAiEnabledChange = viewModel::onAiEnabledChange,
                onGrantPermissionClick = { permission ->
                    val status = state.permissions.first { it.permission == permission }.status
                    viewModel.onGrantPermissionClick(permission)
                    systemActions.requestPermission(permission, status)
                },
                onMiuiConfirmedChange = viewModel::onMiuiConfirmedChange,
                onOpenMiuiSettingsClick = systemActions.openMiuiSettings,
                onFinishClick = viewModel::onFinishClick,
            ),
    )
}

@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.screenInsets(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = LikkaSpacing.l),
            verticalArrangement = Arrangement.Center,
        ) {
            StepBody(state, actions)
        }
        StepIndicator(stepCount = state.steps.size, currentIndex = state.currentIndex)
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = LikkaSpacing.m),
            verticalArrangement = Arrangement.spacedBy(LikkaSpacing.xs),
        ) {
            StepActions(state, actions)
        }
    }
}

@Composable
private fun StepBody(
    state: OnboardingUiState,
    actions: OnboardingActions,
) {
    when (state.step) {
        OnboardingStep.WELCOME -> WelcomeStep()
        OnboardingStep.HOW_IT_WORKS -> HowItWorksStep()
        OnboardingStep.PRIVACY -> PrivacyStep(state.aiEnabled, actions.onAiEnabledChange)
        OnboardingStep.PERMISSIONS -> PermissionsStep(state, actions.onGrantPermissionClick)
        OnboardingStep.MIUI -> MiuiStep(state.miuiConfirmed, actions.onMiuiConfirmedChange, actions.onOpenMiuiSettingsClick)
        OnboardingStep.DONE -> DoneStep()
    }
}

@Composable
private fun StepActions(
    state: OnboardingUiState,
    actions: OnboardingActions,
) {
    val isLast = state.step == OnboardingStep.DONE
    LikkaButton(
        text = stringResource(primaryLabel(state.step)),
        onClick = if (isLast) actions.onFinishClick else actions.onNextClick,
        enabled = state.canContinue,
        disabledReason = stringResource(R.string.onboarding_permissions_required).takeIf { !state.canContinue },
    )
    if (!state.isFirstStep) {
        LikkaButton(text = stringResource(R.string.onboarding_back), onClick = actions.onBackClick, variant = LikkaButtonVariant.TEXT)
    }
}

@StringRes
private fun primaryLabel(step: OnboardingStep): Int =
    when (step) {
        OnboardingStep.WELCOME -> R.string.onboarding_start
        OnboardingStep.DONE -> R.string.onboarding_finish
        else -> R.string.onboarding_next
    }

@LikkaThemePreviews
@Composable
private fun OnboardingWelcomePreview() {
    LikkaPreview { OnboardingContent(OnboardingUiState.preview(OnboardingStep.WELCOME), OnboardingActions()) }
}

@LikkaThemePreviews
@Composable
private fun OnboardingHowItWorksPreview() {
    LikkaPreview { OnboardingContent(OnboardingUiState.preview(OnboardingStep.HOW_IT_WORKS), OnboardingActions()) }
}

@LikkaThemePreviews
@Composable
private fun OnboardingPrivacyPreview() {
    LikkaPreview { OnboardingContent(OnboardingUiState.preview(OnboardingStep.PRIVACY), OnboardingActions()) }
}

@LikkaThemePreviews
@Composable
private fun OnboardingPermissionsPreview() {
    LikkaPreview { OnboardingContent(OnboardingUiState.preview(OnboardingStep.PERMISSIONS), OnboardingActions()) }
}

@LikkaThemePreviews
@Composable
private fun OnboardingPermissionsDeniedPreview() {
    LikkaPreview {
        OnboardingContent(
            OnboardingUiState.preview(OnboardingStep.PERMISSIONS, permissionStatus = PermissionStatus.DENIED),
            OnboardingActions(),
        )
    }
}

@LikkaThemePreviews
@Composable
private fun OnboardingMiuiPreview() {
    LikkaPreview { OnboardingContent(OnboardingUiState.preview(OnboardingStep.MIUI), OnboardingActions()) }
}

@LikkaThemePreviews
@Composable
private fun OnboardingDonePreview() {
    LikkaPreview { OnboardingContent(OnboardingUiState.preview(OnboardingStep.DONE), OnboardingActions()) }
}
