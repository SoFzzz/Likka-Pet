package com.likkapet.presentation.components

import android.provider.Settings
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
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
import com.likkapet.R
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaSpriteSize
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
}

/** Pixel geometry of the sprite sheet (design system §1.7, "Formato del arte"). */
object SpriteMetrics {
    const val FRAME_PX = 96
    const val CHARACTER_HEIGHT_PX = 88
    const val FEET_LINE_Y = 92
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
 * first frame is shown. Compose extras (`sparkles`, `z`, `sweat_drop`, `aura`) are not drawn here.
 * If the sheet is missing from assets, a neutral marker with the character's footprint is drawn.
 */
@Composable
fun LikkaSprite(
    pose: LikkaPose,
    size: Dp,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
    contentDescription: String = stringResource(pose.descriptionRes),
) {
    val density = LocalDensity.current
    val pixelScale = spriteScale(with(density) { size.toPx() })
    val frameSize = with(density) { (SpriteMetrics.FRAME_PX * pixelScale).toDp() }
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
    val position = rememberPlayback(clip, idleClip = sheet.data.clipFor(LikkaSpriteSheet.IDLE_POSE))
    SpriteFrameCanvas(
        image = sheet.image,
        frame = position.clip.frames[position.frameIndex],
        pixelScale = pixelScale,
        isMirrored = position.clip.mirror != mirrored,
        modifier = spriteModifier,
    )
}

@Composable
private fun rememberLikkaSpriteSheet(): LoadedSpriteSheet? {
    val assets = LocalContext.current.assets
    val sheet by produceState(LikkaSpriteAssets.cachedOrNull(), assets) {
        if (value == null) value = withContext(Dispatchers.IO) { LikkaSpriteAssets.load(assets) }
    }
    return sheet
}

/** `ANIMATOR_DURATION_SCALE == 0` (*Quitar animaciones*) means first frames only (design system §1.6). */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

@Composable
private fun rememberPlayback(
    clip: SpriteClip,
    idleClip: SpriteClip?,
): PlaybackPosition {
    val areAnimationsEnabled = rememberAnimationsEnabled()
    var position by remember(clip) { mutableStateOf(PlaybackPosition(clip, frameIndex = 0)) }
    LaunchedEffect(clip, areAnimationsEnabled) {
        if (!areAnimationsEnabled) return@LaunchedEffect
        while (true) {
            delay(position.clip.frames[position.frameIndex].durationMs)
            position = nextPlaybackPosition(position, idleClip) ?: break
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
    modifier: Modifier,
) {
    Canvas(modifier = modifier) {
        val drawnSize = IntSize(frame.width * pixelScale, frame.height * pixelScale)
        val topLeft =
            IntOffset((this.size.width.toInt() - drawnSize.width) / 2, (this.size.height.toInt() - drawnSize.height) / 2)
        scale(scaleX = if (isMirrored) -1f else 1f, scaleY = 1f, pivot = center) {
            drawImage(
                image = image,
                srcOffset = IntOffset(frame.x, frame.y),
                srcSize = IntSize(frame.width, frame.height),
                dstOffset = topLeft,
                dstSize = drawnSize,
                filterQuality = FilterQuality.None,
            )
        }
    }
}

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
