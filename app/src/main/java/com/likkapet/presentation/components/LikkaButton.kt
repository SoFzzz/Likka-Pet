package com.likkapet.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.likkapet.R
import com.likkapet.presentation.theme.DISABLED_ALPHA
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaShapes
import com.likkapet.presentation.theme.LikkaSpacing
import com.likkapet.presentation.theme.LocalLikkaButtonColors

/** Button variants of design system §1.8. */
enum class LikkaButtonVariant { PRIMARY, WARNING, DANGER, SECONDARY, TEXT }

/**
 * Pill button in one of the five variants of §1.8. Filled variants read `LocalLikkaButtonColors`
 * (same fill in both themes); Secondary and Text read `colorScheme.primary`. When disabled it
 * drops to 38% opacity and, if [disabledReason] is given, says why underneath (never a bare grey
 * button).
 */
@Composable
fun LikkaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: LikkaButtonVariant = LikkaButtonVariant.PRIMARY,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
    icon: ImageVector? = null,
    disabledReason: String? = null,
) {
    val widthModifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
    Column(
        modifier = modifier.then(widthModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LikkaSpacing.xs),
    ) {
        ButtonForVariant(text, onClick, variant, enabled, widthModifier, icon)
        if (!enabled && disabledReason != null) {
            Text(
                text = disabledReason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ButtonForVariant(
    text: String,
    onClick: () -> Unit,
    variant: LikkaButtonVariant,
    enabled: Boolean,
    widthModifier: Modifier,
    icon: ImageVector?,
) {
    val sizeModifier = widthModifier.height(LikkaComponentSize.buttonHeight)
    when (variant) {
        LikkaButtonVariant.PRIMARY,
        LikkaButtonVariant.WARNING,
        LikkaButtonVariant.DANGER,
        -> {
            FilledVariant(text, onClick, variant, enabled, sizeModifier, icon)
        }

        LikkaButtonVariant.SECONDARY -> {
            val accent = MaterialTheme.colorScheme.primary
            OutlinedButton(
                onClick = onClick,
                modifier = sizeModifier,
                enabled = enabled,
                shape = LikkaShapes.full,
                border = BorderStroke(LikkaComponentSize.borderButton, accent.copy(alpha = if (enabled) 1f else DISABLED_ALPHA)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            ) { ButtonContent(text, icon) }
        }

        LikkaButtonVariant.TEXT -> {
            TextButton(
                onClick = onClick,
                modifier = widthModifier.heightIn(min = LikkaComponentSize.minTouchTarget),
                enabled = enabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) { ButtonContent(text, icon) }
        }
    }
}

@Composable
private fun FilledVariant(
    text: String,
    onClick: () -> Unit,
    variant: LikkaButtonVariant,
    enabled: Boolean,
    modifier: Modifier,
    icon: ImageVector?,
) {
    val (container, onContainer) = filledColors(variant)
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = LikkaShapes.full,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = onContainer,
                disabledContainerColor = container.copy(alpha = DISABLED_ALPHA),
                disabledContentColor = onContainer.copy(alpha = DISABLED_ALPHA),
            ),
    ) { ButtonContent(text, icon) }
}

@Composable
private fun filledColors(variant: LikkaButtonVariant): Pair<Color, Color> {
    val colors = LocalLikkaButtonColors.current
    return when (variant) {
        LikkaButtonVariant.WARNING -> colors.warningContainer to colors.onWarningContainer
        LikkaButtonVariant.DANGER -> colors.dangerContainer to colors.onDangerContainer
        else -> colors.primaryContainer to colors.onPrimaryContainer
    }
}

@Composable
private fun ButtonContent(
    text: String,
    icon: ImageVector?,
) {
    if (icon != null) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(LikkaComponentSize.iconChip))
        Spacer(Modifier.width(LikkaSpacing.s))
    }
    Text(text = text, style = MaterialTheme.typography.labelLarge)
}

@LikkaThemePreviews
@Composable
private fun LikkaButtonPreview() {
    LikkaPreview {
        Column(
            modifier = Modifier.padding(LikkaSpacing.m),
            verticalArrangement = Arrangement.spacedBy(LikkaSpacing.s),
        ) {
            val label = stringResource(R.string.pause_button)
            LikkaButton(text = label, onClick = {}, icon = Icons.Rounded.Check)
            LikkaButton(text = label, onClick = {}, variant = LikkaButtonVariant.WARNING)
            LikkaButton(text = label, onClick = {}, variant = LikkaButtonVariant.DANGER)
            LikkaButton(text = label, onClick = {}, variant = LikkaButtonVariant.SECONDARY)
            LikkaButton(text = label, onClick = {}, variant = LikkaButtonVariant.TEXT)
            LikkaButton(text = label, onClick = {}, enabled = false, disabledReason = stringResource(R.string.pause_unavailable_no_pauses))
        }
    }
}
