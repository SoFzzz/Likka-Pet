package com.likkapet.presentation.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.likkapet.R
import com.likkapet.presentation.components.LikkaPreview
import com.likkapet.presentation.components.LikkaThemePreviews
import com.likkapet.presentation.components.SettingsSwitchRow
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaSpacing

/**
 * What leaves the phone and what never does (documentación §8), plus the AI switch. Shared by
 * onboarding step 3 and Settings › "Qué datos se envían". Clear tone, no sarcasm (design system §1.9).
 */
@Composable
fun PrivacyBody(
    aiEnabled: Boolean,
    onAiEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LikkaSpacing.m)) {
        Text(
            text = stringResource(R.string.privacy_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        PrivacyList(
            titleRes = R.string.privacy_sent_title,
            itemRes =
                listOf(
                    R.string.privacy_sent_app,
                    R.string.privacy_sent_minutes,
                    R.string.privacy_sent_angle,
                    R.string.privacy_sent_level,
                ),
            icon = Icons.Rounded.CheckCircle,
        )
        PrivacyList(
            titleRes = R.string.privacy_never_title,
            itemRes =
                listOf(
                    R.string.privacy_never_identity,
                    R.string.privacy_never_content,
                    R.string.privacy_never_history,
                ),
            icon = Icons.Rounded.Block,
        )
        PrivacyList(
            titleRes = R.string.privacy_ip_title,
            itemRes = listOf(R.string.privacy_ip_body),
            icon = Icons.Rounded.Info,
        )
        Text(
            text = stringResource(R.string.privacy_limits_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSwitchRow(
            label = stringResource(R.string.privacy_ai_switch),
            checked = aiEnabled,
            onCheckedChange = onAiEnabledChange,
        )
        Text(
            text = stringResource(R.string.privacy_ai_switch_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrivacyList(
    titleRes: Int,
    itemRes: List<Int>,
    icon: ImageVector,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LikkaSpacing.s)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        itemRes.forEach { res ->
            Row(horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.s)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(LikkaComponentSize.iconChip),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@LikkaThemePreviews
@Composable
private fun PrivacyBodyPreview() {
    LikkaPreview {
        PrivacyBody(aiEnabled = true, onAiEnabledChange = {}, modifier = Modifier.padding(LikkaSpacing.m))
    }
}
