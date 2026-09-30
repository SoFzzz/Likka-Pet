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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.likkapet.R
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaSpriteSize
import com.likkapet.presentation.theme.levelColor
import com.likkapet.presentation.theme.stageFillColor
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
    HOP("hop", R.string.pose_hop),
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
 * Likka on screen. Placeholder until `sprites/likka.png` exists: draws a neutral marker on the
 * character's real footprint (88 px tall, feet on y = 92, integer scale) so layouts already have
 * the final size. [pose] only feeds the accessibility description for now; the sheet loader that
 * reads `likka.json` / `likka_poses.json` replaces the body of this composable, not its callers.
 */
@Composable
fun LikkaSprite(
    pose: LikkaPose,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(pose.descriptionRes),
) {
    val density = LocalDensity.current
    val scale = spriteScale(with(density) { size.toPx() })
    val frameSize = with(density) { (SpriteMetrics.FRAME_PX * scale).toDp() }
    val markerColor = MaterialTheme.colorScheme.outline
    Canvas(
        modifier =
            modifier
                .size(frameSize)
                .semantics {
                    this.contentDescription = contentDescription
                    role = Role.Image
                },
    ) {
        val unit = scale.toFloat()
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
