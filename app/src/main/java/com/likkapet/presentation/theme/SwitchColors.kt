package com.likkapet.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Switch colors for Settings rows (design system §2.5): active track `colorScheme.primary`,
 * inactive track `bg.switchTrackInactive`: 3.25:1 on the dark background (cocoa) and 4.34:1 on the
 * light one (cream), but only 2.94:1 on plum, so a switch never sits on a plum card (§1.1,
 * `ColorContrastTest`). The inactive thumb is light in both themes so it stays visible on that
 * mid-tone track.
 */
@Composable
fun likkaSwitchColors(): SwitchColors {
    val scheme = MaterialTheme.colorScheme
    return SwitchDefaults.colors(
        checkedThumbColor = scheme.onPrimary,
        checkedTrackColor = scheme.primary,
        checkedBorderColor = scheme.primary,
        uncheckedThumbColor = switchThumbInactiveColor(),
        uncheckedTrackColor = switchTrackInactiveColor(),
        uncheckedBorderColor = switchTrackInactiveColor(),
        disabledCheckedThumbColor = scheme.onPrimary.copy(alpha = DISABLED_ALPHA),
        disabledCheckedTrackColor = scheme.primary.copy(alpha = DISABLED_ALPHA),
        disabledCheckedBorderColor = scheme.primary.copy(alpha = DISABLED_ALPHA),
    )
}

/** `bg.switchTrackInactive`: the same value in both themes. */
@Composable
fun switchTrackInactiveColor(): Color = LikkaColors.SwitchTrackInactive

/** Cream on dark, white on light, so the thumb reads against [switchTrackInactiveColor]. */
@Composable
fun switchThumbInactiveColor(): Color = if (LocalLikkaIsLightTheme.current) LikkaColors.LightSurface else LikkaColors.Cream

/** Material's own disabled opacity for content; also the 38% of design system §1.8. */
const val DISABLED_ALPHA = 0.38f
