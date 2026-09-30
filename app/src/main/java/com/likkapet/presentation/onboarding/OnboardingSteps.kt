package com.likkapet.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.likkapet.R
import com.likkapet.presentation.components.LikkaButton
import com.likkapet.presentation.components.LikkaButtonVariant
import com.likkapet.presentation.components.LikkaPose
import com.likkapet.presentation.components.LikkaStage
import com.likkapet.presentation.permissions.PermissionsChecklist
import com.likkapet.presentation.privacy.PrivacyBody
import com.likkapet.presentation.state.AppPermission
import com.likkapet.presentation.state.MiuiTask
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaShapes
import com.likkapet.presentation.theme.LikkaSpacing
import com.likkapet.presentation.theme.LikkaSpriteSize
import com.likkapet.presentation.theme.levelColor

/** Step title in `headline` (the screen title of design system §1.2), announced as a heading. */
@Composable
private fun StepTitle(
    text: String,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = textAlign,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
fun WelcomeStep(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LikkaSpacing.m),
    ) {
        LikkaStage(pose = LikkaPose.IDLE, size = LikkaSpriteSize.onboarding)
        StepTitle(stringResource(R.string.onboarding_welcome_title), TextAlign.Center)
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun HowItWorksStep(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LikkaSpacing.m)) {
        StepTitle(stringResource(R.string.onboarding_how_title))
        Text(
            text = stringResource(R.string.onboarding_how_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LevelCard(1, Icons.Rounded.Visibility, R.string.onboarding_level_1_title, R.string.onboarding_level_1_body)
        LevelCard(2, Icons.AutoMirrored.Rounded.DirectionsWalk, R.string.onboarding_level_2_title, R.string.onboarding_level_2_body)
        LevelCard(3, Icons.Rounded.Warning, R.string.onboarding_level_3_title, R.string.onboarding_level_3_body)
    }
}

/** One of the three "Cómo funciona" cards; the border takes the level color (design system §1.1). */
@Composable
private fun LevelCard(
    level: Int,
    icon: ImageVector,
    titleRes: Int,
    bodyRes: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = LikkaShapes.m,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(LikkaComponentSize.borderLevel, levelColor(level)),
    ) {
        Row(modifier = Modifier.padding(LikkaSpacing.m), horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.m)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(LikkaComponentSize.iconPermission),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(LikkaSpacing.xs)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun PrivacyStep(
    aiEnabled: Boolean,
    onAiEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LikkaSpacing.m)) {
        StepTitle(stringResource(R.string.onboarding_privacy_title))
        PrivacyBody(aiEnabled = aiEnabled, onAiEnabledChange = onAiEnabledChange)
    }
}

@Composable
fun PermissionsStep(
    state: OnboardingUiState,
    onGrantClick: (AppPermission) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LikkaSpacing.m)) {
        StepTitle(stringResource(R.string.onboarding_permissions_title))
        Text(
            text = stringResource(R.string.onboarding_permissions_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (state.hasDeniedPermission) {
            LikkaStage(
                pose = LikkaPose.WORRIED,
                size = LikkaSpriteSize.onboarding,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        PermissionsChecklist(permissions = state.permissions, onGrantClick = onGrantClick)
    }
}

@Composable
fun MiuiStep(
    confirmed: Set<MiuiTask>,
    onConfirmedChange: (MiuiTask, Boolean) -> Unit,
    onOpenSettingsClick: (MiuiTask) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LikkaSpacing.m)) {
        StepTitle(stringResource(R.string.onboarding_miui_title))
        Text(
            text = stringResource(R.string.onboarding_miui_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        MiuiTask.entries.forEach { task ->
            MiuiTaskCard(
                task = task,
                isConfirmed = task in confirmed,
                onConfirmedChange = { onConfirmedChange(task, it) },
                onOpenSettingsClick = { onOpenSettingsClick(task) },
            )
        }
    }
}

@Composable
private fun MiuiTaskCard(
    task: MiuiTask,
    isConfirmed: Boolean,
    onConfirmedChange: (Boolean) -> Unit,
    onOpenSettingsClick: () -> Unit,
) {
    val (titleRes, bodyRes) = miuiCopy(task)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LikkaShapes.m,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(LikkaComponentSize.borderThin, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(LikkaSpacing.m), verticalArrangement = Arrangement.spacedBy(LikkaSpacing.s)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LikkaButton(
                text = stringResource(R.string.onboarding_miui_open_settings),
                onClick = onOpenSettingsClick,
                variant = LikkaButtonVariant.SECONDARY,
                fillWidth = false,
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = LikkaComponentSize.minTouchTarget)
                        .toggleable(value = isConfirmed, role = Role.Checkbox, onValueChange = onConfirmedChange),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.s),
            ) {
                Checkbox(checked = isConfirmed, onCheckedChange = null)
                Text(
                    text = stringResource(R.string.onboarding_miui_confirmed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun miuiCopy(task: MiuiTask): Pair<Int, Int> =
    when (task) {
        MiuiTask.AUTOSTART -> R.string.onboarding_miui_autostart_title to R.string.onboarding_miui_autostart_body
        MiuiTask.BATTERY -> R.string.onboarding_miui_battery_title to R.string.onboarding_miui_battery_body
        MiuiTask.POPUP -> R.string.onboarding_miui_popup_title to R.string.onboarding_miui_popup_body
    }

@Composable
fun DoneStep(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LikkaSpacing.m),
    ) {
        LikkaStage(pose = LikkaPose.HAPPY, size = LikkaSpriteSize.onboarding)
        StepTitle(stringResource(R.string.onboarding_done_title), TextAlign.Center)
        Text(
            text = stringResource(R.string.onboarding_done_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}
