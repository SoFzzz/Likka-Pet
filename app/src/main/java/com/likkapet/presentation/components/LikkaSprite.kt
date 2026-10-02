package com.likkapet.presentation.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.likkapet.R
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaExtrasColors
import com.likkapet.presentation.theme.LikkaMotion
import com.likkapet.presentation.theme.LikkaSpriteSize
import com.likkapet.presentation.theme.LocalLikkaExtrasColors
import com.likkapet.presentation.theme.levelColor
import com.likkapet.presentation.theme.stageFillColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.max

/** Keys of `sprites/likka_poses.json` (design system §1.7); each one maps to an Aseprite tag there. */
enum class LikkaPose(
    val key: String,
    @StringRes val descriptionRes: Int,
) {
    IDLE("idle", R.string.pose_idle),
    PEEK("peek", R.string.pose_peek),
    ANNOYED("annoyed", R.string.pose_annoyed),
    FURY("fury", R.string.pose_fury),
    WORRIED("worried", R.string.pose_worried),
    SLEEPING("sleeping", R.string.pose_sleeping),
    SIT("sit", R.string.pose_sit),
    PERCH("perch", R.string.pose_perch),
    DRAGGED("dragged", R.string.pose_dragged),
    HAPPY("happy", R.string.pose_happy),
    GOODBYE("goodbye", R.string.pose_goodbye),
    WALK_LEFT("walk_left", R.string.pose_walking),
    WALK_DIAG_DOWN_LEFT("walk_diag_down_left", R.string.pose_walking),
    WALK_RIGHT("walk_right", R.string.pose_walking),
    WALK_DIAG_DOWN_RIGHT("walk_diag_down_right", R.string.pose_walking),
    WALK_DOWN("walk_down", R.string.pose_walking),
    WALK_UP("walk_up", R.string.pose_walking),
    ;

    companion object {
        /** The pose with this `likka_poses.json` key (e.g. a planner `MotionPose.key`), or [IDLE] if unknown. */
        fun fromKey(key: String): LikkaPose = entries.firstOrNull { it.key == key } ?: IDLE
    }
}

/** Pixel geometry of the sprite sheet (design system §1.7, "Formato del arte"). */
object SpriteMetrics {
    const val FRAME_PX = 96
    const val CHARACTER_HEIGHT_PX = 88
    const val FEET_LINE_Y = 92

    // Thickness of the overlay's level halo, in sprite pixels (design system §1.7, extra `halo`).
    const val HALO_PX = 2
}

/**
 * Integer scale for a target character height in physical pixels: `floor(targetPx / 88)`, never
 * fractional (design system §1.7), and at least 1× so a tiny target still draws.
 */
fun spriteScale(targetHeightPx: Float): Int = max(1, floor(targetHeightPx / SpriteMetrics.CHARACTER_HEIGHT_PX).toInt())

/**
 * Likka on screen, animated from `assets/sprites/` (design system §1.7): the pose's tag and playback
 * mode come from `likka_poses.json`, frame positions and per-frame durations from `likka.json`.
 * Drawn at an integer scale without filtering. [mirrored] flips the frame in X on top of the pose's
 * own `mirror` (the UI uses it for `peek` on the left edge). With *Quitar animaciones* on, only the
 * first frame is shown and the extras hold still. If the sheet is missing from assets, a neutral
 * marker with the character's footprint is drawn.
 *
 * The pose's Compose extras from `likka_poses.json` (`sparkles`, `z`, `sweat_drop`, `aura`) are drawn
 * here, on the sprite-pixel grid ([SpriteExtras]). [halo] draws the overlay's level halo (Levels 1–2,
 * design system §1.7): the frame's silhouette in that color, [SpriteMetrics.HALO_PX] sprite pixels
 * around Likka, so the drawn area grows by that much on every side. [footShadow] draws the overlay's
 * shadow under the feet.
 */
@Composable
fun LikkaSprite(
    pose: LikkaPose,
    size: Dp,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
    halo: Color? = null,
    footShadow: Boolean = false,
    contentDescription: String = stringResource(pose.descriptionRes),
) {
    val density = LocalDensity.current
    val pixelScale = spriteScale(with(density) { size.toPx() })
    val haloPx = if (halo != null) SpriteMetrics.HALO_PX else 0
    val frameSize = with(density) { ((SpriteMetrics.FRAME_PX + 2 * haloPx) * pixelScale).toDp() }
    val spriteModifier =
        modifier
            .size(frameSize)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Image
            }
    val sheet = rememberLikkaSpriteSheet()
    val clip = sheet?.data?.clipFor(pose.key)
    if (sheet == null || clip == null) {
        SpritePlaceholder(pixelScale, spriteModifier)
        return
    }
    val areAnimationsEnabled = rememberAnimationsEnabled()
    val position = rememberPlayback(clip, idleClip = sheet.data.clipFor(LikkaSpriteSheet.IDLE_POSE), areAnimationsEnabled)
    val frame = position.clip.frames[position.frameIndex]
    SpriteFrameCanvas(
        image = sheet.image,
        frame = frame,
        pixelScale = pixelScale,
        isMirrored = position.clip.mirror != mirrored,
        decorations =
            SpriteDecorations(
                halo = halo,
                feet = sheet.feetSpans[frame].takeIf { footShadow },
                extras = position.clip.extras,
                extrasElapsedMs = rememberExtrasClock(position.clip.extras, areAnimationsEnabled),
                colors = LocalLikkaExtrasColors.current,
            ),
        modifier = spriteModifier,
    )
}

/** Everything drawn around the frame: the halo, the foot shadow ([feet] non-null) and the pose's extras. */
private data class SpriteDecorations(
    val halo: Color?,
    val feet: IntRange?,
    val extras: Set<SpriteExtra>,
    val extrasElapsedMs: Long,
    val colors: LikkaExtrasColors,
)

@Composable
private fun rememberLikkaSpriteSheet(): LoadedSpriteSheet? {
    val assets = LocalContext.current.assets
    val sheet by produceState(LikkaSpriteAssets.cachedOrNull(), assets) {
        if (value == null) value = withContext(Dispatchers.IO) { LikkaSpriteAssets.load(assets) }
    }
    return sheet
}

// The extras' own time, in steps of LikkaMotion.EXTRAS_STEP_MS. Frozen at 0 with animations off
// (design system §1.6), not ticking at all for a pose without extras, and paused while not started
// (a hidden overlay window, an app screen in the background), so nothing wakes up for nothing.
@Composable
private fun rememberExtrasClock(
    extras: Set<SpriteExtra>,
    areAnimationsEnabled: Boolean,
): Long {
    var elapsedMs by remember(extras) { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(extras, areAnimationsEnabled, lifecycle) {
        if (extras.isEmpty() || !areAnimationsEnabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(LikkaMotion.EXTRAS_STEP_MS)
                elapsedMs += LikkaMotion.EXTRAS_STEP_MS
            }
        }
    }
    return if (areAnimationsEnabled) elapsedMs else 0L
}

// With animations off (design system §1.6) only the first frame of the pose is shown. Paused, where
// it was, while not started, like the extras' clock.
@Composable
private fun rememberPlayback(
    clip: SpriteClip,
    idleClip: SpriteClip?,
    areAnimationsEnabled: Boolean,
): PlaybackPosition {
    var position by remember(clip) { mutableStateOf(PlaybackPosition(clip, frameIndex = 0)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(clip, areAnimationsEnabled, lifecycle) {
        if (!areAnimationsEnabled) {
            position = PlaybackPosition(clip, frameIndex = 0)
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(position.clip.frames[position.frameIndex].durationMs)
                position = nextPlaybackPosition(position, idleClip) ?: break
            }
        }
    }
    return position
}

@Composable
private fun SpriteFrameCanvas(
    image: ImageBitmap,
    frame: SpriteFrame,
    pixelScale: Int,
    isMirrored: Boolean,
    decorations: SpriteDecorations,
    modifier: Modifier,
) {
    Canvas(modifier = modifier) {
        val drawnSize = IntSize(frame.width * pixelScale, frame.height * pixelScale)
        val topLeft =
            IntOffset((this.size.width.toInt() - drawnSize.width) / 2, (this.size.height.toInt() - drawnSize.height) / 2)
        val grid = SpriteGrid(topLeft, pixelScale, isMirrored = false)
        // Under the frame, in this order: shadow, aura, halo. They all mirror with the frame.
        scale(scaleX = if (isMirrored) -1f else 1f, scaleY = 1f, pivot = center) {
            decorations.feet?.let { drawFootShadow(grid, it, decorations.colors.footShadow) }
            if (SpriteExtra.AURA in decorations.extras) drawAura(image, frame, grid, drawnSize, decorations)
            decorations.halo?.let { drawSilhouetteRing(image, frame, grid, drawnSize, SpriteMetrics.HALO_PX, it) }
            drawFrame(image, frame, topLeft, drawnSize, colorFilter = null)
        }
        // Over the frame and never mirrored, so a z still reads as a z; only their spot is mirrored.
        drawPointExtras(grid.copy(isMirrored = isMirrored), decorations)
    }
}

/** Sprite-pixel coordinates of the frame on the canvas; [isMirrored] flips x inside the 96 px frame. */
private data class SpriteGrid(
    val topLeft: IntOffset,
    val pixelScale: Int,
    val isMirrored: Boolean,
) {
    /** Left edge of something [width] sprite pixels wide whose left edge is [spriteX] in the unmirrored frame. */
    fun x(
        spriteX: Int,
        width: Int = 1,
    ): Int {
        val x = if (isMirrored) SpriteMetrics.FRAME_PX - spriteX - width else spriteX
        return topLeft.x + x * pixelScale
    }

    fun y(spriteY: Int): Int = topLeft.y + spriteY * pixelScale
}

private fun DrawScope.drawFootShadow(
    grid: SpriteGrid,
    feet: IntRange,
    color: Color,
) {
    SpriteExtras.footShadowRows(feet).forEach { (y, xs) ->
        drawRect(
            color = color,
            topLeft = Offset(grid.x(xs.first).toFloat(), grid.y(y).toFloat()),
            size = Size(((xs.last - xs.first + 1) * grid.pixelScale).toFloat(), grid.pixelScale.toFloat()),
        )
    }
}

// The aura of `fury` (design system §1.6): raspberry next to Likka, light raspberry outside, growing
// and shrinking one sprite pixel at a time.
private fun DrawScope.drawAura(
    image: ImageBitmap,
    frame: SpriteFrame,
    grid: SpriteGrid,
    drawnSize: IntSize,
    decorations: SpriteDecorations,
) {
    val radius = SpriteExtras.auraRadius(decorations.extrasElapsedMs)
    drawSilhouetteRing(image, frame, grid, drawnSize, radius, decorations.colors.auraOuter)
    drawSilhouetteRing(image, frame, grid, drawnSize, (radius + 1) / 2, decorations.colors.auraInner)
}

// The frame's silhouette tinted [color], drawn at every sprite-pixel shift within [radius]: a ring
// around Likka that follows the pixel grid.
private fun DrawScope.drawSilhouetteRing(
    image: ImageBitmap,
    frame: SpriteFrame,
    grid: SpriteGrid,
    drawnSize: IntSize,
    radius: Int,
    color: Color,
) {
    val silhouette = ColorFilter.tint(color, BlendMode.SrcIn)
    dilationOffsetsFor(radius).forEach { (dx, dy) ->
        val offset = IntOffset(grid.topLeft.x + dx * grid.pixelScale, grid.topLeft.y + dy * grid.pixelScale)
        drawFrame(image, frame, offset, drawnSize, silhouette)
    }
}

private fun DrawScope.drawPointExtras(
    grid: SpriteGrid,
    decorations: SpriteDecorations,
) {
    val colors = decorations.colors
    val elapsedMs = decorations.extrasElapsedMs
    if (SpriteExtra.SPARKLES in decorations.extras) {
        SpriteExtras.sparkleSpots(elapsedMs).forEach { drawPattern(grid, SpriteExtras.SPARKLE, it, colors.sparkle, colors) }
    }
    if (SpriteExtra.SLEEP_Z in decorations.extras) {
        SpriteExtras.sleepZSpots(elapsedMs).forEach { drawPattern(grid, SpriteExtras.SLEEP_Z, it, colors.sleepZ, colors) }
    }
    if (SpriteExtra.SWEAT_DROP in decorations.extras) {
        drawPattern(grid, SpriteExtras.SWEAT_DROP, SpriteExtras.sweatDropSpot(elapsedMs), colors.sweatDrop, colors)
    }
}

private fun DrawScope.drawPattern(
    grid: SpriteGrid,
    pattern: PixelPattern,
    at: SpritePoint,
    fill: Color,
    colors: LikkaExtrasColors,
) {
    val left = grid.x(at.x, pattern.width)
    val pixelSize = Size(grid.pixelScale.toFloat(), grid.pixelScale.toFloat())
    pattern.pixels.forEach { pixel ->
        val color =
            when (pixel.role) {
                PixelPattern.OUTLINE -> colors.outline
                PixelPattern.SHINE -> colors.sweatDropShine
                else -> fill
            }
        drawRect(color, Offset((left + pixel.x * grid.pixelScale).toFloat(), grid.y(at.y + pixel.y).toFloat()), pixelSize)
    }
}

private fun DrawScope.drawFrame(
    image: ImageBitmap,
    frame: SpriteFrame,
    topLeft: IntOffset,
    drawnSize: IntSize,
    colorFilter: ColorFilter?,
) = drawImage(
    image = image,
    srcOffset = IntOffset(frame.x, frame.y),
    srcSize = IntSize(frame.width, frame.height),
    dstOffset = topLeft,
    dstSize = drawnSize,
    filterQuality = FilterQuality.None,
    colorFilter = colorFilter,
)

// The shifts of each ring radius, computed once: the halo and the aura draw the silhouette at every one.
private val dilationOffsetsByRadius =
    (0..maxOf(SpriteExtras.AURA_RADII.max(), SpriteMetrics.HALO_PX)).map(SpriteExtras::dilationOffsets)

private fun dilationOffsetsFor(radius: Int): List<Pair<Int, Int>> = dilationOffsetsByRadius[radius]

/** Neutral marker on the character's real footprint (88 px tall, feet on y = 92) when there is no sheet. */
@Composable
private fun SpritePlaceholder(
    pixelScale: Int,
    modifier: Modifier,
) {
    val markerColor = MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier) {
        val unit = pixelScale.toFloat()
        val markerWidth = MARKER_WIDTH_PX * unit
        drawRoundRect(
            color = markerColor,
            topLeft = Offset((this.size.width - markerWidth) / 2f, CHARACTER_TOP_PX * unit),
            size = Size(markerWidth, SpriteMetrics.CHARACTER_HEIGHT_PX * unit),
            cornerRadius = CornerRadius(MARKER_CORNER_PX * unit),
        )
    }
}

/**
 * Likka on the cream stage circle with a 3dp level-colored border (dashboard, onboarding, level 3
 * panel; design system §1.7). The circle is as wide as the sprite frame, so it is never smaller
 * than Likka's displayed height.
 */
@Composable
fun LikkaStage(
    pose: LikkaPose,
    modifier: Modifier = Modifier,
    size: Dp = LikkaSpriteSize.dashboard,
    level: Int = 0,
    contentDescription: String = stringResource(pose.descriptionRes),
) {
    val density = LocalDensity.current
    val scale = spriteScale(with(density) { size.toPx() })
    val diameter = with(density) { (SpriteMetrics.FRAME_PX * scale).toDp() }
    Box(
        modifier =
            modifier
                .size(diameter)
                .clip(CircleShape)
                .background(stageFillColor())
                .border(LikkaComponentSize.stageBorder, levelColor(level), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        LikkaSprite(pose = pose, size = size, contentDescription = contentDescription)
    }
}

// Placeholder marker footprint, in sprite pixels.
private const val MARKER_WIDTH_PX = 48
private const val MARKER_CORNER_PX = 12
private const val CHARACTER_TOP_PX = SpriteMetrics.FEET_LINE_Y - SpriteMetrics.CHARACTER_HEIGHT_PX
