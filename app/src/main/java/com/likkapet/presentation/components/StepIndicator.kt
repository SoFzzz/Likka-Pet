package com.likkapet.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.likkapet.R
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaSpacing

/**
 * Dots for the onboarding progress (design system §3.2). The current step is a filled dot and the
 * rest are hollow, so the position never depends on color alone; TalkBack hears "Paso N de M".
 */
@Composable
fun StepIndicator(
    stepCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.onboarding_step_indicator, currentIndex + 1, stepCount)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(stepCount) { index ->
            Dot(isCurrent = index == currentIndex)
        }
    }
}

@Composable
private fun Dot(isCurrent: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    val base = Modifier.size(LikkaComponentSize.stepDot).clearAndSetSemantics {}
    Box(
        modifier =
            if (isCurrent) {
                base.background(color, CircleShape)
            } else {
                base.border(LikkaComponentSize.borderThin, MaterialTheme.colorScheme.outline, CircleShape)
            },
    )
}

@LikkaThemePreviews
@Composable
private fun StepIndicatorPreview() {
    LikkaPreview {
        StepIndicator(stepCount = 6, currentIndex = 2, modifier = Modifier.padding(LikkaSpacing.m))
    }
}
