package com.likkapet.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.likkapet.R
import com.likkapet.presentation.components.AppIcon
import com.likkapet.presentation.components.AppIconLoader
import com.likkapet.presentation.components.LikkaPreview
import com.likkapet.presentation.components.LikkaThemePreviews
import com.likkapet.presentation.components.NoAppIcons
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaSpacing
import com.likkapet.presentation.theme.likkaSwitchColors

/**
 * An added app in "Apps vigiladas" (design system §2.5): icon, name with "Quitar" under it, and its
 * switch. 56dp is a minimum: with 200% font the name wraps and the row grows. The switch and
 * "Quitar" are separate targets (≥ 48dp each), so the row itself is not clickable.
 */
@Composable
fun AddedAppRow(
    item: AddedAppUi,
    iconLoader: AppIconLoader,
    onCheckedChange: (Boolean) -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = LikkaComponentSize.settingsRowHeight).padding(vertical = LikkaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.m),
    ) {
        AppIcon(packageName = item.packageName, loader = iconLoader)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            RemoveButton(label = item.label, enabled = item.canRemove, onClick = onRemoveClick)
        }
        Switch(
            checked = item.isWatched,
            onCheckedChange = onCheckedChange,
            enabled = item.canToggle,
            colors = likkaSwitchColors(),
            modifier = Modifier.semantics { contentDescription = item.label },
        )
    }
}

// No confirmation: removing is reversible from "Añadir app" (design system §2.5).
@Composable
private fun RemoveButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.settings_remove_app_description, label)
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = LikkaComponentSize.minTouchTarget).semantics { contentDescription = description },
    ) {
        Text(text = stringResource(R.string.settings_remove_app), style = MaterialTheme.typography.labelLarge)
    }
}

@LikkaThemePreviews
@Composable
private fun AddedAppRowPreview() {
    LikkaPreview {
        Column(modifier = Modifier.padding(horizontal = LikkaSpacing.m)) {
            AddedAppRow(AddedAppUi("com.android.chrome", "Chrome", true, true, true), NoAppIcons, {}, {})
            AddedAppRow(AddedAppUi("org.example.notes", "Notas", true, false, false), NoAppIcons, {}, {})
        }
    }
}
