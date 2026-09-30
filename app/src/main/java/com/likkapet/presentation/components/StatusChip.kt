package com.likkapet.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.likkapet.R
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaShapes
import com.likkapet.presentation.theme.LikkaSpacing

/** Tone of a [StatusChip]: which theme color it takes; the icon and label always repeat the meaning. */
enum class StatusTone { ACTIVE, WARNING, ALERT, MUTED }

/**
 * Pill that states Likka's global state (design system §2.4): icon + label in the tone color, on
 * a transparent fill with a matching 1.5dp border so it never needs plum behind it (ochre text on
 * plum is only 4.3:1, §1.1).
 */
@Composable
fun StatusChip(
    label: String,
    icon: ImageVector,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val color = toneColor(tone)
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = LikkaShapes.full,
        color = Color.Transparent,
        contentColor = color,
        border = BorderStroke(LikkaComponentSize.borderButton, color),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = LikkaSpacing.m, vertical = LikkaSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.s),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(LikkaComponentSize.iconChip))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun toneColor(tone: StatusTone): Color =
    when (tone) {
        StatusTone.ACTIVE -> MaterialTheme.colorScheme.primary
        StatusTone.WARNING -> MaterialTheme.colorScheme.secondary
        StatusTone.ALERT -> MaterialTheme.colorScheme.tertiary
        StatusTone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@LikkaThemePreviews
@Composable
private fun StatusChipPreview() {
    LikkaPreview {
        Column(
            modifier = Modifier.padding(LikkaSpacing.m),
            verticalArrangement = Arrangement.spacedBy(LikkaSpacing.s),
        ) {
            StatusChip(stringResource(R.string.status_protecting), Icons.Rounded.CheckCircle, StatusTone.ACTIVE)
            StatusChip(stringResource(R.string.status_paused), Icons.Rounded.Pause, StatusTone.WARNING)
            StatusChip(stringResource(R.string.status_permission_missing), Icons.Rounded.Warning, StatusTone.ALERT)
            StatusChip(stringResource(R.string.status_disabled), Icons.Rounded.PowerSettingsNew, StatusTone.MUTED)
        }
    }
}
