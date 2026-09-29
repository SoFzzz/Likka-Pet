package com.likkapet.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * Target height of Likka in dp (the character, ≈88 px tall inside the 96×96 px frame),
 * before integer scaling: scale = floor(targetPx / 88), with targetPx = dp × density.
 * Not the frame size: the frame is 96 × scale px, and the overlay window is sized to that frame
 * plus the halo on each side and the bubble.
 */
object LikkaSpriteSize {
    val level1 = 66.dp
    val level2 = 132.dp
    val level3 = 196.dp
    val dashboard = 164.dp
    val onboarding = 100.dp
}
