package com.likkapet.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.likkapet.R
import com.likkapet.domain.EscalationConfig
import com.likkapet.presentation.components.LikkaButton
import com.likkapet.presentation.components.LikkaButtonVariant
import com.likkapet.presentation.components.LikkaPreview
import com.likkapet.presentation.components.LikkaThemePreviews
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaSpacing

/** Bottom sheet that picks the pause length (design system §2.4); it never leaves the dashboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseSheet(
    optionsMinutes: List<Int>,
    selectedMinutes: Int,
    pausesLeft: Int,
    onOptionSelected: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        PauseSheetContent(
            optionsMinutes = optionsMinutes,
            selectedMinutes = selectedMinutes,
            pausesLeft = pausesLeft,
            onOptionSelected = onOptionSelected,
            onConfirm = onConfirm,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PauseSheetContent(
    optionsMinutes: List<Int>,
    selectedMinutes: Int,
    pausesLeft: Int,
    onOptionSelected: (Int) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = LikkaSpacing.m)
                .padding(bottom = LikkaSpacing.m),
        verticalArrangement = Arrangement.spacedBy(LikkaSpacing.m),
    ) {
        Text(text = stringResource(R.string.pause_sheet_title), style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.s)) {
            optionsMinutes.forEach { minutes ->
                PauseChip(minutes = minutes, isSelected = minutes == selectedMinutes, onClick = { onOptionSelected(minutes) })
            }
        }
        Text(
            text = pluralStringResource(R.plurals.pauses_left, pausesLeft, pausesLeft),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LikkaButton(
            text = stringResource(R.string.pause_confirm_button),
            onClick = onConfirm,
            variant = LikkaButtonVariant.WARNING,
        )
    }
}

@Composable
private fun PauseChip(
    minutes: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(text = stringResource(R.string.pause_option_minutes, minutes), style = MaterialTheme.typography.labelLarge) },
        leadingIcon =
            if (isSelected) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(LikkaComponentSize.iconChip)) }
            } else {
                null
            },
    )
}

@LikkaThemePreviews
@Composable
private fun PauseSheetContentPreview() {
    LikkaPreview {
        PauseSheetContent(
            optionsMinutes = EscalationConfig.PAUSE_OPTIONS_MIN,
            selectedMinutes = DashboardLocalState.DEFAULT_PAUSE_MINUTES,
            pausesLeft = EscalationConfig.MAX_PAUSES_PER_DAY,
            onOptionSelected = {},
            onConfirm = {},
            modifier = Modifier.padding(top = LikkaSpacing.m),
        )
    }
}
