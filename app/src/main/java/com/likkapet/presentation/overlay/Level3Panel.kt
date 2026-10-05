package com.likkapet.presentation.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.likkapet.R
import com.likkapet.domain.model.OverlayScene
import com.likkapet.domain.model.ThemeMode
import com.likkapet.presentation.components.LikkaButton
import com.likkapet.presentation.components.LikkaButtonVariant
import com.likkapet.presentation.components.LikkaPose
import com.likkapet.presentation.components.LikkaStage
import com.likkapet.presentation.components.rememberAnimationsEnabled
import com.likkapet.presentation.components.spriteScale
import com.likkapet.presentation.theme.LikkaMotion
import com.likkapet.presentation.theme.LikkaOverlayLayout
import com.likkapet.presentation.theme.LikkaSpacing
import com.likkapet.presentation.theme.LikkaSpriteSize
import com.likkapet.presentation.theme.LikkaTheme
import kotlin.math.roundToInt

/**
 * Level 3 (design system §1.8 "Overlay por nivel", §2.3): Likka `fury` on its stage, the roast (an
 * assertive live region, RNF-U04), the countdown and the Danger "Me rindo" button, filling the
 * whole screen between the system bars (owner decision, 2026-10-05). In portrait they go in one
 * column; in landscape Likka goes on the left and the rest on the right, so the button is on screen
 * without scrolling. It still scrolls when it must, so a 200% font never cuts the button off
 * (RNF-U03). Likka grows onto the panel with `motion.fury` (design system §1.6) and its aura pulses
 * (the `aura` extra of `fury`); the panel itself never moves.
 */
@Composable
fun Level3Panel(
    scene: OverlayScene.Fury,
    onSurrenderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = LikkaOverlayLayout.LEVEL_3_PANEL_ALPHA))
                .padding(LikkaSpacing.l),
        contentAlignment = Alignment.Center,
    ) {
        if (maxWidth > maxHeight) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.l, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FuryStage()
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(LikkaSpacing.m, Alignment.CenterVertically),
                ) {
                    FuryMessage(scene, onSurrenderClick)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LikkaSpacing.m, Alignment.CenterVertically),
            ) {
                FuryStage()
                FuryMessage(scene, onSurrenderClick)
            }
        }
    }
}

@Composable
private fun FuryStage() {
    LikkaStage(
        modifier = Modifier.furyGrowth(),
        pose = LikkaPose.FURY,
        size = LikkaSpriteSize.level3,
        level = FURY_LEVEL,
        contentDescription = stringResource(R.string.overlay_likka_description, stringResource(R.string.pose_fury), FURY_LEVEL),
    )
}

// The roast, the countdown and "Me rindo", in this order in both layouts.
@Composable
private fun FuryMessage(
    scene: OverlayScene.Fury,
    onSurrenderClick: () -> Unit,
) {
    scene.roast?.let { roast ->
        Text(
            text = roast,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
    Text(
        text = pluralStringResource(R.plurals.overlay_level_3_countdown, scene.secondsLeft, scene.secondsLeft),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    LikkaButton(
        text = stringResource(R.string.surrender_button),
        onClick = onSurrenderClick,
        variant = LikkaButtonVariant.DANGER,
    )
}

/**
 * `motion.fury`: the stage grows from one sprite pixel per pixel to its full scale with a springy
 * overshoot, one whole scale step at a time, so every sprite pixel stays a whole number of physical
 * pixels (design system §1.7). Only once per panel, and not at all with animations off.
 */
@Composable
private fun Modifier.furyGrowth(): Modifier {
    val areAnimationsEnabled = rememberAnimationsEnabled()
    val fullScale = spriteScale(with(LocalDensity.current) { LikkaSpriteSize.level3.toPx() })
    val growth = remember { Animatable(if (areAnimationsEnabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (areAnimationsEnabled) growth.animateTo(1f, LikkaMotion.fury) else growth.snapTo(1f)
    }
    return graphicsLayer {
        val scaleSteps = (growth.value * fullScale).roundToInt().coerceAtLeast(1)
        scaleX = scaleSteps.toFloat() / fullScale
        scaleY = scaleX
    }
}

private const val FURY_LEVEL = 3

@Preview(name = "Dark", widthDp = 360, heightDp = 720)
@Composable
private fun Level3PanelPreview() {
    LikkaTheme(ThemeMode.DARK) {
        Level3Panel(
            scene =
                OverlayScene.Fury(
                    roast = "Se acabó la función. Te mando al inicio antes de que el algoritmo te adopte.",
                    secondsLeft = 12,
                ),
            onSurrenderClick = {},
        )
    }
}

@Preview(name = "Dark, landscape", widthDp = 780, heightDp = 360)
@Composable
private fun Level3PanelLandscapePreview() {
    LikkaTheme(ThemeMode.DARK) {
        Level3Panel(
            scene =
                OverlayScene.Fury(
                    roast = "Se acabó la función. Te mando al inicio antes de que el algoritmo te adopte.",
                    secondsLeft = 12,
                ),
            onSurrenderClick = {},
        )
    }
}
