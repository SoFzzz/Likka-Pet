package com.likkapet.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.likkapet.R
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaSpacing
import com.likkapet.presentation.theme.likkaSwitchColors

/** Group heading of Settings (design system §2.5): `caption`, secondary color, short caps. */
@Composable
fun SettingsGroupHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = LikkaSpacing.l, bottom = LikkaSpacing.xs).semantics { heading() },
    )
}

/** 56dp row with a label and a switch; the whole row toggles, so the target is far above 48dp. */
@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = LikkaComponentSize.settingsRowHeight)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.m),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = likkaSwitchColors())
    }
}

/** 56dp row that opens another screen (text link with a chevron). */
@Composable
fun SettingsLinkRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = LikkaComponentSize.settingsRowHeight)
                .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.m),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@LikkaThemePreviews
@Composable
private fun SettingsRowsPreview() {
    LikkaPreview {
        Column(modifier = Modifier.padding(horizontal = LikkaSpacing.m)) {
            SettingsGroupHeader(stringResource(R.string.settings_group_likka))
            SettingsSwitchRow(stringResource(R.string.settings_likka_enabled), checked = true, onCheckedChange = {})
            SettingsSwitchRow(stringResource(R.string.settings_vibration), checked = false, onCheckedChange = {})
            SettingsLinkRow(stringResource(R.string.settings_review_permissions), onClick = {})
        }
    }
}
