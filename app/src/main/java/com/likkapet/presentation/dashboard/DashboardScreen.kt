package com.likkapet.presentation.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.likkapet.R
import com.likkapet.presentation.components.DeactivateDialog
import com.likkapet.presentation.components.LikkaButton
import com.likkapet.presentation.components.LikkaButtonVariant
import com.likkapet.presentation.components.LikkaPreview
import com.likkapet.presentation.components.LikkaStage
import com.likkapet.presentation.components.LikkaThemePreviews
import com.likkapet.presentation.components.LikkaTopBar
import com.likkapet.presentation.components.StatCard
import com.likkapet.presentation.components.StatusChip
import com.likkapet.presentation.components.StatusTone
import com.likkapet.presentation.components.screenInsets
import com.likkapet.presentation.theme.LikkaSpacing
import com.likkapet.presentation.theme.LikkaSpriteSize

/** Everything the dashboard can ask for; the screen forwards them, no logic in the composable. */
data class DashboardActions(
    val onSettingsClick: () -> Unit = {},
    val onGrantPermissionClick: () -> Unit = {},
    val onPauseClick: () -> Unit = {},
    val onPauseSheetDismiss: () -> Unit = {},
    val onPauseOptionSelected: (Int) -> Unit = {},
    val onPauseConfirm: () -> Unit = {},
    val onResumeClick: () -> Unit = {},
    val onActivateClick: () -> Unit = {},
    val onDeactivateClick: () -> Unit = {},
    val onDeactivateConfirm: () -> Unit = {},
    val onDeactivateDismiss: () -> Unit = {},
)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onSettingsClick: () -> Unit,
    onGrantPermissionClick: () -> Unit,
) {
    // Nothing to draw until the store has been read once (a few milliseconds after opening).
    val state = viewModel.uiState.collectAsStateWithLifecycle().value ?: return
    DashboardContent(
        state = state,
        actions =
            DashboardActions(
                onSettingsClick = onSettingsClick,
                onGrantPermissionClick = onGrantPermissionClick,
                onPauseClick = viewModel::onPauseClick,
                onPauseSheetDismiss = viewModel::onPauseSheetDismiss,
                onPauseOptionSelected = viewModel::onPauseOptionSelected,
                onPauseConfirm = viewModel::onPauseConfirm,
                onResumeClick = viewModel::onResumeClick,
                onActivateClick = viewModel::onActivateClick,
                onDeactivateClick = viewModel::onDeactivateClick,
                onDeactivateConfirm = viewModel::onDeactivateConfirm,
                onDeactivateDismiss = viewModel::onDeactivateDismiss,
            ),
    )
}

@Composable
fun DashboardContent(
    state: DashboardUiState,
    actions: DashboardActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.screenInsets()) {
        LikkaTopBar(title = stringResource(R.string.app_name), onSettingsClick = actions.onSettingsClick)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LikkaSpacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LikkaSpacing.m),
        ) {
            LikkaStage(pose = state.status.pose, size = LikkaSpriteSize.dashboard)
            StatusHeader(state)
            Metrics(state)
            PrimaryActions(state, actions)
        }
    }
    if (state.local.isPauseSheetVisible) {
        PauseSheet(
            optionsMinutes = state.pauseOptionsMinutes,
            selectedMinutes = state.local.selectedPauseMinutes,
            pausesLeft = state.pausesLeft,
            onOptionSelected = actions.onPauseOptionSelected,
            onConfirm = actions.onPauseConfirm,
            onDismiss = actions.onPauseSheetDismiss,
        )
    }
    if (state.local.isDeactivateDialogVisible) {
        DeactivateDialog(onConfirm = actions.onDeactivateConfirm, onDismiss = actions.onDeactivateDismiss)
    }
}

@Composable
private fun StatusHeader(state: DashboardUiState) {
    val chip = chipFor(state.status)
    StatusChip(label = stringResource(chip.labelRes), icon = chip.icon, tone = chip.tone)
    Text(
        text = statusMessage(state),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun statusMessage(state: DashboardUiState): String =
    when (state.status) {
        DashboardStatus.PROTECTING, DashboardStatus.AI_DISABLED -> stringResource(R.string.status_message_protecting)
        DashboardStatus.PAUSED -> stringResource(R.string.status_message_paused, state.pausedUntilLabel.orEmpty())
        DashboardStatus.DISABLED -> stringResource(R.string.status_message_disabled)
        DashboardStatus.PERMISSION_MISSING -> stringResource(R.string.status_message_permission_missing)
        DashboardStatus.OFFLINE -> stringResource(R.string.status_message_offline)
    }

@Composable
private fun Metrics(state: DashboardUiState) {
    StatCard(
        value = stringResource(R.string.stat_minutes_value, state.minutesToday),
        label = stringResource(R.string.stat_minutes_label),
        isHero = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.s),
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
    ) {
        StatCard(
            value = state.interventionsToday.toString(),
            label = pluralStringResource(R.plurals.stat_interventions_label, state.interventionsToday),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        StatCard(
            value = state.streakDays.toString(),
            label = pluralStringResource(R.plurals.stat_streak_label, state.streakDays),
            icon = Icons.Rounded.LocalFireDepartment,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun PrimaryActions(
    state: DashboardUiState,
    actions: DashboardActions,
) {
    when (state.status) {
        DashboardStatus.DISABLED -> {
            LikkaButton(text = stringResource(R.string.activate_likka_button), onClick = actions.onActivateClick)
        }

        DashboardStatus.PERMISSION_MISSING -> {
            LikkaButton(text = stringResource(R.string.permission_grant_action), onClick = actions.onGrantPermissionClick)
        }

        DashboardStatus.PAUSED -> {
            LikkaButton(text = stringResource(R.string.resume_button), onClick = actions.onResumeClick)
        }

        else -> {
            PauseAction(state, actions.onPauseClick)
        }
    }
    if (state.status != DashboardStatus.DISABLED) {
        LikkaButton(
            text = stringResource(R.string.deactivate_likka_button),
            onClick = actions.onDeactivateClick,
            variant = LikkaButtonVariant.DANGER,
        )
    }
}

@Composable
private fun PauseAction(
    state: DashboardUiState,
    onPauseClick: () -> Unit,
) {
    val isAvailable = state.pauseAvailability == PauseAvailability.AVAILABLE
    LikkaButton(
        text = stringResource(R.string.pause_button),
        onClick = onPauseClick,
        enabled = isAvailable,
        disabledReason = pauseUnavailableReason(state.pauseAvailability),
    )
    if (isAvailable) {
        Text(
            text = pluralStringResource(R.plurals.pauses_left, state.pausesLeft, state.pausesLeft),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun pauseUnavailableReason(availability: PauseAvailability): String? =
    when (availability) {
        PauseAvailability.AVAILABLE -> null
        PauseAvailability.NO_PAUSES_LEFT -> stringResource(R.string.pause_unavailable_no_pauses)
        PauseAvailability.LEVEL_3_ACTIVE -> stringResource(R.string.pause_unavailable_level_3)
    }

private data class ChipSpec(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val tone: StatusTone,
)

private fun chipFor(status: DashboardStatus): ChipSpec =
    when (status) {
        DashboardStatus.PAUSED -> {
            ChipSpec(R.string.status_paused, Icons.Rounded.Pause, StatusTone.WARNING)
        }

        DashboardStatus.DISABLED -> {
            ChipSpec(R.string.status_disabled, Icons.Rounded.PowerSettingsNew, StatusTone.MUTED)
        }

        DashboardStatus.PERMISSION_MISSING -> {
            ChipSpec(R.string.status_permission_missing, Icons.Rounded.Warning, StatusTone.ALERT)
        }

        else -> {
            ChipSpec(R.string.status_protecting, Icons.Rounded.CheckCircle, StatusTone.ACTIVE)
        }
    }

@LikkaThemePreviews
@Composable
private fun DashboardProtectingPreview() {
    LikkaPreview { DashboardContent(state = DashboardUiState.preview(), actions = DashboardActions()) }
}

@LikkaThemePreviews
@Composable
private fun DashboardPausedPreview() {
    LikkaPreview {
        DashboardContent(state = DashboardUiState.preview(DashboardStatus.PAUSED), actions = DashboardActions())
    }
}

@LikkaThemePreviews
@Composable
private fun DashboardDisabledPreview() {
    LikkaPreview {
        DashboardContent(state = DashboardUiState.preview(DashboardStatus.DISABLED), actions = DashboardActions())
    }
}

@LikkaThemePreviews
@Composable
private fun DashboardPermissionMissingPreview() {
    LikkaPreview {
        DashboardContent(state = DashboardUiState.preview(DashboardStatus.PERMISSION_MISSING), actions = DashboardActions())
    }
}

@LikkaThemePreviews
@Composable
private fun DashboardOfflinePreview() {
    LikkaPreview {
        DashboardContent(state = DashboardUiState.preview(DashboardStatus.OFFLINE), actions = DashboardActions())
    }
}

@LikkaThemePreviews
@Composable
private fun DashboardNoPausesLeftPreview() {
    LikkaPreview {
        DashboardContent(
            state = DashboardUiState.preview().copy(pausesLeft = 0, pauseAvailability = PauseAvailability.NO_PAUSES_LEFT),
            actions = DashboardActions(),
        )
    }
}
