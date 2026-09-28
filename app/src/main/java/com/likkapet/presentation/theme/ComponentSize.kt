package com.likkapet.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * Fixed component measures (design system §1.3, §1.5, §1.8). Only [buttonHeight] is documented in
 * §5; the rest name the values that §1.3/§1.5/§1.8/§2.5 already state in prose, so composables
 * never carry a bare dp literal.
 */
object LikkaComponentSize {
    val buttonHeight = 52.dp
    val minTouchTarget = 48.dp
    val settingsRowHeight = 56.dp

    val borderThin = 1.dp
    val borderButton = 1.5.dp
    val borderLevel = 2.dp
    val stageBorder = 3.dp

    val iconStandard = 24.dp
    val iconChip = 20.dp
    val iconPermission = 32.dp

    // Launcher icon of an added app (design system §2.5, "Añadir app").
    val appIcon = 40.dp

    val stepDot = 8.dp
}
