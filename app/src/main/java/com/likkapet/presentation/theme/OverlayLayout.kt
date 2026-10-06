package com.likkapet.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * Overlay measures (design system §1.8 "Overlay por nivel" and "Burbuja de diálogo"). The Level 3
 * panel fills the screen between the system bars (owner decision, 2026-10-05, replacing the 80%
 * panel), cocoa at 96% as in §1.8; the bubble values are new in phase 4a.
 */
object LikkaOverlayLayout {
    const val LEVEL_3_PANEL_ALPHA = 0.96f

    // A roast of up to 25 words wraps into a few lines instead of a window as wide as the screen.
    val bubbleMaxWidth = 240.dp

    // The bubble's tail pointing at Likka.
    val bubbleTailWidth = 16.dp
    val bubbleTailHeight = 8.dp
}
