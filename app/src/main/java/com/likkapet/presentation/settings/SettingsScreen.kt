package com.likkapet.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.likkapet.R
import com.likkapet.domain.model.AppLanguage
import com.likkapet.domain.model.LikkaSettings
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.ThemeMode
import com.likkapet.presentation.components.AppIconLoader
import com.likkapet.presentation.components.DeactivateDialog
import com.likkapet.presentation.components.LikkaPreview
import com.likkapet.presentation.components.LikkaThemePreviews
import com.likkapet.presentation.components.LikkaTopBar
import com.likkapet.presentation.components.NoAppIcons
import com.likkapet.presentation.components.SettingsGroupHeader
import com.likkapet.presentation.components.SettingsLinkRow
import com.likkapet.presentation.components.SettingsSwitchRow
import com.likkapet.presentation.components.screenInsets
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaSpacing

/** Everything Settings can ask for; the screen forwards them, no logic in the composable. */
data class SettingsActions(
    val onBackClick: () -> Unit = {},
    val onLikkaEnabledChange: (Boolean) -> Unit = {},
    val onDeactivateConfirm: () -> Unit = {},
    val onDeactivateDismiss: () -> Unit = {},
    val onVibrationChange: (Boolean) -> Unit = {},
    val onThemeSelected: (ThemeMode) -> Unit = {},
    val onLanguageSelected: (AppLanguage) -> Unit = {},
    val onWatchedAppChange: (TargetApp, Boolean) -> Unit = { _, _ -> },
    val onAddedAppChange: (String, Boolean) -> Unit = { _, _ -> },
    val onRemoveApp: (String) -> Unit = {},
    val onAddAppClick: () -> Unit = {},
    val onAiEnabledChange: (Boolean) -> Unit = {},
    val onPrivacyClick: () -> Unit = {},
    val onReviewPermissionsClick: () -> Unit = {},
    val onAboutClick: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    iconLoader: AppIconLoader,
    onBackClick: () -> Unit,
    onAddAppClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onReviewPermissionsClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value ?: return
    SettingsContent(
        state = state,
        iconLoader = iconLoader,
        actions =
            SettingsActions(
                onBackClick = onBackClick,
                onLikkaEnabledChange = viewModel::onLikkaEnabledChange,
                onDeactivateConfirm = viewModel::onDeactivateConfirm,
                onDeactivateDismiss = viewModel::onDeactivateDismiss,
                onVibrationChange = viewModel::onVibrationChange,
                onThemeSelected = viewModel::onThemeSelected,
                onLanguageSelected = viewModel::onLanguageSelected,
                onWatchedAppChange = viewModel::onWatchedAppChange,
                onAddedAppChange = viewModel::onAddedAppChange,
                onRemoveApp = viewModel::onRemoveApp,
                onAddAppClick = onAddAppClick,
                onAiEnabledChange = viewModel::onAiEnabledChange,
                onPrivacyClick = onPrivacyClick,
                onReviewPermissionsClick = onReviewPermissionsClick,
                onAboutClick = onAboutClick,
            ),
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    iconLoader: AppIconLoader = NoAppIcons,
) {
    Column(modifier = modifier.screenInsets()) {
        LikkaTopBar(title = stringResource(R.string.settings_title), onBackClick = actions.onBackClick)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LikkaSpacing.l)) {
            LikkaGroup(state, actions)
            AppearanceGroup(state.themeMode, state.appLanguage, actions.onThemeSelected, actions.onLanguageSelected)
            WatchedAppsGroup(state, iconLoader, actions)
            AiGroup(state, actions)
            SettingsLinkRow(stringResource(R.string.settings_review_permissions), actions.onReviewPermissionsClick)
            SettingsLinkRow(stringResource(R.string.settings_about), actions.onAboutClick)
        }
    }
    if (state.isDeactivateDialogVisible) {
        DeactivateDialog(onConfirm = actions.onDeactivateConfirm, onDismiss = actions.onDeactivateDismiss)
    }
}

@Composable
private fun LikkaGroup(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    SettingsGroupHeader(stringResource(R.string.settings_group_likka))
    SettingsSwitchRow(stringResource(R.string.settings_likka_enabled), state.isLikkaEnabled, actions.onLikkaEnabledChange)
    SettingsSwitchRow(stringResource(R.string.settings_vibration), state.isVibrationEnabled, actions.onVibrationChange)
}

@Composable
private fun AppearanceGroup(
    themeMode: ThemeMode,
    appLanguage: AppLanguage,
    onThemeSelected: (ThemeMode) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    SettingsGroupHeader(stringResource(R.string.settings_group_appearance))
    Text(
        text = stringResource(R.string.settings_theme),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = LikkaSpacing.s),
    )
    val themeOptions = listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        themeOptions.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == themeMode,
                onClick = { onThemeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                modifier = Modifier.heightIn(min = LikkaComponentSize.minTouchTarget),
            ) {
                Text(text = stringResource(themeLabel(mode)), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    Text(
        text = stringResource(R.string.settings_language),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = LikkaSpacing.m, bottom = LikkaSpacing.s),
    )
    val languageOptions = listOf(AppLanguage.SYSTEM, AppLanguage.ES, AppLanguage.EN)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        languageOptions.forEachIndexed { index, lang ->
            SegmentedButton(
                selected = lang == appLanguage,
                onClick = { onLanguageSelected(lang) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = languageOptions.size),
                modifier = Modifier.heightIn(min = LikkaComponentSize.minTouchTarget),
            ) {
                Text(text = stringResource(languageLabel(lang)), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun themeLabel(mode: ThemeMode): Int =
    when (mode) {
        ThemeMode.DARK -> R.string.settings_theme_dark
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.SYSTEM -> R.string.settings_theme_system
    }

private fun languageLabel(language: AppLanguage): Int =
    when (language) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.ES -> R.string.settings_language_es
        AppLanguage.EN -> R.string.settings_language_en
    }

/** The 4 default apps, then the added ones in alphabetical order, then "Añadir app" (design system §2.5). */
@Composable
private fun WatchedAppsGroup(
    state: SettingsUiState,
    iconLoader: AppIconLoader,
    actions: SettingsActions,
) {
    SettingsGroupHeader(stringResource(R.string.settings_group_watched_apps))
    state.watchedApps.forEach { item ->
        SettingsSwitchRow(
            label = item.app.displayName,
            checked = item.isWatched,
            onCheckedChange = { actions.onWatchedAppChange(item.app, it) },
            enabled = item.canToggle,
        )
    }
    state.addedApps.forEach { item ->
        AddedAppRow(
            item = item,
            iconLoader = iconLoader,
            onCheckedChange = { actions.onAddedAppChange(item.packageName, it) },
            onRemoveClick = { actions.onRemoveApp(item.packageName) },
        )
    }
    if (state.hasNoVisibleApps) {
        Text(
            text = stringResource(R.string.settings_watched_apps_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = LikkaSpacing.s),
        )
    }
    SettingsLinkRow(stringResource(R.string.settings_add_app), actions.onAddAppClick)
    Text(
        text = stringResource(R.string.settings_watched_apps_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AiGroup(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    SettingsGroupHeader(stringResource(R.string.settings_group_ai))
    SettingsSwitchRow(stringResource(R.string.settings_ai_messages), state.isAiEnabled, actions.onAiEnabledChange)
    SettingsLinkRow(stringResource(R.string.settings_privacy_link), actions.onPrivacyClick)
}

@LikkaThemePreviews
@Composable
private fun SettingsContentPreview() {
    LikkaPreview { SettingsContent(state = SettingsUiState.preview(), actions = SettingsActions()) }
}

@LikkaThemePreviews
@Composable
private fun SettingsWatchedAppsPreview() {
    LikkaPreview {
        Column(modifier = Modifier.padding(horizontal = LikkaSpacing.m)) {
            WatchedAppsGroup(SettingsUiState.preview(), NoAppIcons, SettingsActions())
        }
    }
}

@LikkaThemePreviews
@Composable
private fun SettingsNoWatchedAppsPreview() {
    val empty = buildSettingsUiState(LikkaSettings(likkaEnabled = true), launcherApps = emptyList(), isDeactivateDialogVisible = false)
    LikkaPreview {
        Column(modifier = Modifier.padding(horizontal = LikkaSpacing.m)) {
            WatchedAppsGroup(empty, NoAppIcons, SettingsActions())
        }
    }
}
