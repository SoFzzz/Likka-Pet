package com.likkapet.presentation.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring

/**
 * Motion tokens (design system §1.6). Movement cadence and timings (hop, walk, return) are not
 * redefined here: they live only in `EscalationConfig` (MOVEMENT_STEP_FPS, LEVEL_1_HOP_INTERVAL_SEC,
 * LEVEL_2_WALK_MIN_SEC, LEVEL_2_STOP_SEC, LEVEL_2_RETURN_DELAY_SEC), and so does the Level 2 vibration
 * the shake below goes with (LEVEL_2_VIBRATION_MS). Everything moves in whole sprite pixels.
 */
object LikkaMotion {
    const val FAST_MS = 150
    const val STANDARD_MS = 300
    val easing = FastOutSlowInEasing

    // Level 1 slides in from its edge; Level 3 grows on its panel.
    val enter = spring<Float>(dampingRatio = 0.6f)
    val fury = spring<Float>(dampingRatio = 0.4f)

    // Level 2 appears with a horizontal shake of 1 sprite pixel, spread over the vibration.
    val shakeSpritePx = listOf(1, -1, 1, -1, 0)

    // Frame cadence of the drawn extras (sparkles, z, sweat drop), and the aura's pulse (every 1 s).
    const val EXTRAS_STEP_MS = 250L
    const val AURA_PULSE_MS = 1_000L
}
