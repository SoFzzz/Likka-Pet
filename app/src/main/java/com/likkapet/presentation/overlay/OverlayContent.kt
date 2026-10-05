package com.likkapet.presentation.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import com.likkapet.R
import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.model.LocalReaction
import com.likkapet.domain.model.MotionMode
import com.likkapet.domain.model.MotionPose
import com.likkapet.domain.model.OverlayEdge
import com.likkapet.domain.model.OverlayMotion
import com.likkapet.domain.model.OverlayScene
import com.likkapet.domain.model.ShownReaction
import com.likkapet.domain.model.ThemeMode
import com.likkapet.presentation.components.LikkaPose
import com.likkapet.presentation.components.LikkaSprite
import com.likkapet.presentation.components.rememberAnimationsEnabled
import com.likkapet.presentation.components.spriteScale
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaMotion
import com.likkapet.presentation.theme.LikkaSpriteSize
import com.likkapet.presentation.theme.LikkaTheme
import com.likkapet.presentation.theme.levelColor
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/** What the overlay sends back to the service: the UI only sends events (documentación §10). */
class OverlayActions(
    val onSurrenderClick: () -> Unit,
    val onOpenApp: () -> Unit,
    // A tap on Likka at Level 1 or 2; the third in a row makes Likka react (RF-O12).
    val onTap: () -> Unit,
    // The measured window content and the sprite pixel at its scale, for OverlayMotionPlanner's geometry.
    val onMeasured: (size: IntSize, spritePixelPx: Int) -> Unit,
    // Where Likka's touch box sits inside the window: it moves when the bubble changes size or side,
    // and a drag compensates, so Likka stays under the finger.
    val onLikkaPlaced: (offsetInWindow: IntOffset) -> Unit,
)

/**
 * What the service adds to a scene (RF-O12, design system §1.6): the local reaction the bubble shows
 * instead of the roast, and [shakeId], which changes every time the Level 2 vibration plays, so the
 * shake goes with it (0 = no shake yet in this window).
 */
data class OverlayEffects(
    val reaction: ShownReaction? = null,
    val shakeId: Int = 0,
) {
    companion object {
        val NONE = OverlayEffects()
    }
}

/**
 * The overlay window's content for one [OverlayScene] (design system §1.8 "Overlay por nivel").
 * Rendered by the service inside `LikkaTheme(ThemeMode.DARK)`. Stateless except for the Level 1
 * roast's unfolding and the entrance, shake and growth animations, which only change how it is
 * drawn: the window's position always comes from the planner.
 */
@Composable
fun OverlayContent(
    scene: OverlayScene,
    effects: OverlayEffects,
    actions: OverlayActions,
) {
    when (scene) {
        is OverlayScene.Companion -> CompanionOverlay(scene, effects, actions)
        is OverlayScene.Resting -> RestingOverlay(scene, actions)
        is OverlayScene.Fury -> Level3Panel(scene, actions.onSurrenderClick)
        OverlayScene.Withdrawn, OverlayScene.Hidden, is OverlayScene.Ejected -> Unit
    }
}

/**
 * Levels 1–2: Likka without a stage, with the level halo and the foot shadow, and the roast bubble
 * above it, aligned with the edge Likka is on. Level 1 starts folded: a tap unfolds the roast for
 * [EscalationConfig.LEVEL_1_ROAST_EXPANDED_SEC] and a long press opens the app (RF-O02). A local
 * reaction replaces the roast while it lasts (RF-O12); a poke also shows Likka `annoyed`
 * (design system §1.7). Level 1 slides in from its edge (`motion.enter`) and Level 2 shakes one
 * sprite pixel with its vibration, both in whole sprite pixels and never with animations off.
 */
@Composable
private fun CompanionOverlay(
    scene: OverlayScene.Companion,
    effects: OverlayEffects,
    actions: OverlayActions,
) {
    val spriteSize = spriteSizeFor(scene.level)
    val spritePixelPx = spriteScale(with(LocalDensity.current) { spriteSize.toPx() })
    val areAnimationsEnabled = rememberAnimationsEnabled()
    val reaction = effects.reaction
    val pose = poseFor(scene.motion, reaction)
    var isRoastUnfolded by remember { mutableStateOf(false) }
    LaunchedEffect(isRoastUnfolded) {
        if (isRoastUnfolded) {
            delay(EscalationConfig.LEVEL_1_ROAST_EXPANDED_SEC.seconds)
            isRoastUnfolded = false
        }
    }
    val alignment = alignmentFor(scene.motion)
    val bubbleText = reaction?.text ?: scene.roast
    Column(
        modifier =
            Modifier
                .reportSize(spritePixelPx, actions.onMeasured)
                .entranceAndShake(scene, effects.shakeId, spritePixelPx, areAnimationsEnabled),
        horizontalAlignment = alignment,
    ) {
        bubbleText?.let { text ->
            RoastBubble(
                roast = text,
                level = scene.level,
                isExpanded = reaction != null || scene.level > FIRST_LEVEL || isRoastUnfolded,
                tailAlignment = alignment,
            )
        }
        // At least 48dp to touch, even where Level 1 draws at 1× (RNF-U02); Likka stays on its edge and on its feet.
        Box(
            modifier =
                Modifier
                    .defaultMinSize(LikkaComponentSize.minTouchTarget, LikkaComponentSize.minTouchTarget)
                    .onGloballyPositioned { actions.onLikkaPlaced(it.positionInParent().round()) }
                    .then(likkaTouchModifier(scene.level, actions, onUnfold = { isRoastUnfolded = true })),
            contentAlignment = spriteAlignmentFor(scene.motion),
        ) {
            LikkaSprite(
                pose = pose,
                size = spriteSize,
                mirrored = isMirrored(scene.motion, pose),
                halo = levelColor(scene.level),
                footShadow = true,
                contentDescription =
                    stringResource(R.string.overlay_likka_description, stringResource(pose.descriptionRes), scene.level),
            )
        }
    }
}

// No ripple: a rectangle flashing over the app underneath would break the pixel art; Likka's
// reactions are the feedback. Level 1 taps unfold the roast, Level 2 taps only count.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun likkaTouchModifier(
    level: Int,
    actions: OverlayActions,
    onUnfold: () -> Unit,
): Modifier =
    if (level == FIRST_LEVEL) {
        Modifier.combinedClickable(
            interactionSource = null,
            indication = null,
            onClickLabel = stringResource(R.string.overlay_show_roast),
            onLongClickLabel = stringResource(R.string.overlay_open_app),
            onLongClick = actions.onOpenApp,
            onClick = {
                onUnfold()
                actions.onTap()
            },
        )
    } else {
        Modifier.clickable(
            interactionSource = null,
            indication = null,
            onClickLabel = stringResource(R.string.overlay_poke_likka),
            onClick = actions.onTap,
        )
    }

/**
 * Level 1 enters by sliding in from its edge with `motion.enter` once it is first placed in this
 * window; a hop is instantaneous (`motion.hop`), so it never slides again. Level 2 shakes
 * horizontally with [shakeId]. The window stays where the planner put it: only the drawing moves,
 * in whole sprite pixels (design system §1.6), and nothing moves with animations off.
 */
@Composable
private fun Modifier.entranceAndShake(
    scene: OverlayScene.Companion,
    shakeId: Int,
    spritePixelPx: Int,
    areAnimationsEnabled: Boolean,
): Modifier {
    val hiddenFraction = remember { Animatable(1f) }
    var entranceEdge by remember { mutableStateOf<OverlayEdge?>(null) }
    val firstMotion = scene.motion
    LaunchedEffect(firstMotion != null) {
        if (firstMotion == null || hiddenFraction.value == 0f) return@LaunchedEffect
        entranceEdge = firstMotion.edge
        if (scene.level == FIRST_LEVEL && firstMotion.edge != null && areAnimationsEnabled) {
            hiddenFraction.animateTo(0f, LikkaMotion.enter)
        } else {
            hiddenFraction.snapTo(0f)
        }
    }
    var shakeSpritePx by remember { mutableIntStateOf(0) }
    LaunchedEffect(shakeId) {
        if (shakeId == NO_SHAKE || !areAnimationsEnabled) return@LaunchedEffect
        val stepMs = EscalationConfig.LEVEL_2_VIBRATION_MS / LikkaMotion.shakeSpritePx.size
        LikkaMotion.shakeSpritePx.forEach { offset ->
            shakeSpritePx = offset
            delay(stepMs)
        }
        shakeSpritePx = 0
    }
    // Until the entrance starts, a Level 1 about to slide is drawn already outside, so its first frame never flashes in place.
    val pendingEdge = firstMotion?.edge?.takeIf { scene.level == FIRST_LEVEL && areAnimationsEnabled }
    return graphicsLayer {
        val edge = entranceEdge ?: pendingEdge
        val extent = if (edge == OverlayEdge.BOTTOM) size.height else size.width
        val slidPx = (hiddenFraction.value * extent / spritePixelPx).roundToInt() * spritePixelPx
        val shakePx = shakeSpritePx * spritePixelPx
        translationX =
            when (edge) {
                OverlayEdge.LEFT -> -slidPx + shakePx
                OverlayEdge.RIGHT -> slidPx + shakePx
                else -> shakePx
            }.toFloat()
        translationY = if (edge == OverlayEdge.BOTTOM) slidPx.toFloat() else 0f
    }
}

/** Farewell (`goodbye`) or pause (`sit`): Likka alone, still, with the calm halo and its foot shadow. */
@Composable
private fun RestingOverlay(
    scene: OverlayScene.Resting,
    actions: OverlayActions,
) {
    val spriteSize = spriteSizeFor(scene.sizeLevel)
    val spritePixelPx = spriteScale(with(LocalDensity.current) { spriteSize.toPx() })
    LikkaSprite(
        pose = LikkaPose.fromKey(scene.pose.key),
        size = spriteSize,
        modifier = Modifier.reportSize(spritePixelPx, actions.onMeasured),
        halo = levelColor(CALM_LEVEL),
        footShadow = true,
    )
}

// The callback is remembered: a new lambda on every recomposition makes Compose call
// onSizeChanged again with the same size, and each of those would reach the planner as a new
// geometry (Level 2 recomposes on every walking step).
@Composable
private fun Modifier.reportSize(
    spritePixelPx: Int,
    onMeasured: (IntSize, Int) -> Unit,
): Modifier {
    val callback = remember(spritePixelPx, onMeasured) { { size: IntSize -> onMeasured(size, spritePixelPx) } }
    return onSizeChanged(callback)
}

// A poke shows Likka annoyed while the phrase lasts, except under the finger (design system §1.7).
private fun poseFor(
    motion: OverlayMotion?,
    reaction: ShownReaction?,
): LikkaPose {
    val isPoked = reaction?.reaction == LocalReaction.POKE && motion?.mode != MotionMode.DRAGGED
    return if (isPoked) LikkaPose.ANNOYED else LikkaPose.fromKey(motion?.pose?.key ?: MotionPose.PEEK.key)
}

private fun spriteSizeFor(level: Int): Dp = if (level >= SECOND_LEVEL) LikkaSpriteSize.level2 else LikkaSpriteSize.level1

private fun alignmentFor(motion: OverlayMotion?): Alignment.Horizontal =
    when (motion?.edge) {
        OverlayEdge.LEFT -> Alignment.Start
        OverlayEdge.RIGHT -> Alignment.End
        OverlayEdge.BOTTOM, null -> Alignment.CenterHorizontally
    }

private fun spriteAlignmentFor(motion: OverlayMotion?): Alignment =
    when (motion?.edge) {
        OverlayEdge.LEFT -> Alignment.BottomStart
        OverlayEdge.RIGHT -> Alignment.BottomEnd
        OverlayEdge.BOTTOM, null -> Alignment.BottomCenter
    }

// The `peek` art is cut off on its right side, so on the left edge it is mirrored (design system §1.7).
private fun isMirrored(
    motion: OverlayMotion?,
    pose: LikkaPose,
): Boolean = pose == LikkaPose.PEEK && motion?.edge == OverlayEdge.LEFT

private const val CALM_LEVEL = 0
private const val FIRST_LEVEL = 1
private const val SECOND_LEVEL = 2
private const val NO_SHAKE = 0

private val previewActions =
    OverlayActions(onSurrenderClick = {}, onOpenApp = {}, onTap = {}, onMeasured = { _, _ -> }, onLikkaPlaced = {})

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF3D1B1C)
@Composable
private fun CompanionOverlayPreview() {
    LikkaTheme(ThemeMode.DARK) {
        OverlayContent(
            scene =
                OverlayScene.Companion(
                    level = 2,
                    roast = "27 minutos en TikTok. Qué dedicación.",
                    motion = OverlayMotion(x = 0, y = 0, pose = MotionPose.ANNOYED, mode = MotionMode.STOPPED, edge = null),
                ),
            effects = OverlayEffects.NONE,
            actions = previewActions,
        )
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF3D1B1C)
@Composable
private fun CompanionReactionPreview() {
    LikkaTheme(ThemeMode.DARK) {
        OverlayContent(
            scene =
                OverlayScene.Companion(
                    level = 1,
                    roast = "Psst… tu cuello acaba de pedir asilo en mi bosque.",
                    motion = OverlayMotion(x = 0, y = 0, pose = MotionPose.PEEK, mode = MotionMode.ON_EDGE, edge = OverlayEdge.RIGHT),
                ),
            effects = OverlayEffects(reaction = ShownReaction(LocalReaction.POKE, "Si me tocas tanto, empiezo a cobrar por hora.")),
            actions = previewActions,
        )
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF3D1B1C)
@Composable
private fun RestingOverlayPreview() {
    LikkaTheme(ThemeMode.DARK) {
        OverlayContent(
            scene = OverlayScene.Resting(pose = MotionPose.SIT, sizeLevel = 1, motion = null),
            effects = OverlayEffects.NONE,
            actions = previewActions,
        )
    }
}
