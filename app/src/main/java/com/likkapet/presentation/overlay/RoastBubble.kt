package com.likkapet.presentation.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.likkapet.R
import com.likkapet.domain.model.ThemeMode
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaOverlayLayout
import com.likkapet.presentation.theme.LikkaShapes
import com.likkapet.presentation.theme.LikkaSpacing
import com.likkapet.presentation.theme.LikkaTheme
import com.likkapet.presentation.theme.levelColor

/**
 * The roast bubble of Levels 1–2 (design system §1.8 "Burbuja de diálogo"): plum surface, 2dp
 * border of the level color, a tail pointing down at Likka. Collapsed (Level 1 before a tap) it only
 * shows an ellipsis, but TalkBack still reads the roast. Either way the roast is a polite live
 * region, announced when it appears (RNF-U04).
 */
@Composable
fun RoastBubble(
    roast: String,
    level: Int,
    isExpanded: Boolean,
    tailAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    val borderColor = levelColor(level)
    Column(modifier = modifier) {
        Surface(
            modifier =
                Modifier
                    .widthIn(max = LikkaOverlayLayout.bubbleMaxWidth)
                    .border(LikkaComponentSize.borderLevel, borderColor, LikkaShapes.m),
            shape = LikkaShapes.m,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            if (isExpanded) {
                Text(
                    text = roast,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier =
                        Modifier
                            .padding(LikkaSpacing.m)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                )
            } else {
                Text(
                    text = stringResource(R.string.overlay_bubble_collapsed),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier =
                        Modifier
                            .padding(horizontal = LikkaSpacing.m, vertical = LikkaSpacing.xs)
                            .clearAndSetSemantics {
                                contentDescription = roast
                                liveRegion = LiveRegionMode.Polite
                            },
                )
            }
        }
        BubbleTail(
            color = borderColor,
            modifier = Modifier.align(tailAlignment).padding(horizontal = LikkaSpacing.l),
        )
    }
}

@Composable
private fun BubbleTail(
    color: Color,
    modifier: Modifier,
) {
    Canvas(modifier = modifier.size(LikkaOverlayLayout.bubbleTailWidth, LikkaOverlayLayout.bubbleTailHeight)) {
        val tail =
            Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
        drawPath(tail, color)
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF3D1B1C)
@Composable
private fun RoastBubblePreview() {
    LikkaTheme(ThemeMode.DARK) {
        Column {
            RoastBubble(
                roast = "Psst… tu cuello acaba de pedir asilo en mi bosque.",
                level = 2,
                isExpanded = true,
                tailAlignment = Alignment.CenterHorizontally,
            )
            RoastBubble(roast = "Psst…", level = 1, isExpanded = false, tailAlignment = Alignment.Start)
        }
    }
}
