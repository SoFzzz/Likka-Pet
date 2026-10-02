package com.likkapet.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.likkapet.R
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaShapes
import com.likkapet.presentation.theme.LikkaSpacing

/**
 * Dashboard statistic (design system §1.8): number in `display` (hero, amber) or `headline`
 * (cream), label in `caption` below and an optional icon above. Plum surface with a 1dp outline
 * border, `shape.m` and `space.m` padding. Reads as one phrase for TalkBack.
 */
@Composable
fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isHero: Boolean = false,
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = LikkaShapes.m,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(LikkaComponentSize.borderThin, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(LikkaSpacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LikkaSpacing.xs, Alignment.CenterVertically),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(LikkaComponentSize.iconStandard),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = value,
                style = if (isHero) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineLarge,
                color = if (isHero) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@LikkaThemePreviews
@Composable
private fun StatCardPreview() {
    LikkaPreview {
        Column(
            modifier = Modifier.padding(LikkaSpacing.m),
            verticalArrangement = Arrangement.spacedBy(LikkaSpacing.s),
        ) {
            StatCard(
                value = stringResource(R.string.stat_minutes_value, 23),
                label = stringResource(R.string.stat_minutes_label),
                isHero = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.s)) {
                StatCard(
                    value = "4",
                    label = pluralStringResource(R.plurals.stat_interventions_label, 4),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = "6",
                    label = pluralStringResource(R.plurals.stat_streak_label, 6),
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.LocalFireDepartment,
                )
            }
        }
    }
}
