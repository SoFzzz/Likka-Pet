package com.likkapet.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.likkapet.R
import com.likkapet.presentation.state.PermissionStatus
import com.likkapet.presentation.theme.LikkaComponentSize
import com.likkapet.presentation.theme.LikkaShapes
import com.likkapet.presentation.theme.LikkaSpacing

/**
 * Permission checklist card (design system §1.8). Pending: primary-colored border and a "Conceder"
 * button. Granted: check icon and secondary text. Denied: `error` (raspberry-light) border and a
 * "Reintentar" button. The status is always spelled out in text, never only in the border color.
 */
@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    status: PermissionStatus,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = LikkaShapes.m,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(borderWidth(status), borderColor(status)),
    ) {
        Row(
            modifier = Modifier.padding(LikkaSpacing.m),
            horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.m),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(LikkaComponentSize.iconPermission),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(LikkaSpacing.xs), modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PermissionStatusLine(status = status, onGrantClick = onGrantClick)
            }
        }
    }
}

@Composable
private fun PermissionStatusLine(
    status: PermissionStatus,
    onGrantClick: () -> Unit,
) {
    when (status) {
        PermissionStatus.GRANTED -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LikkaSpacing.xs)) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(LikkaComponentSize.iconChip),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.permission_granted),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PermissionStatus.PENDING -> {
            LikkaButton(
                text = stringResource(R.string.permission_grant_button),
                onClick = onGrantClick,
                variant = LikkaButtonVariant.SECONDARY,
                fillWidth = false,
            )
        }

        PermissionStatus.DENIED -> {
            Text(
                text = stringResource(R.string.permission_denied),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            LikkaButton(
                text = stringResource(R.string.permission_retry_button),
                onClick = onGrantClick,
                variant = LikkaButtonVariant.SECONDARY,
                fillWidth = false,
            )
        }
    }
}

@Composable
private fun borderColor(status: PermissionStatus): Color =
    when (status) {
        PermissionStatus.PENDING -> MaterialTheme.colorScheme.primary
        PermissionStatus.GRANTED -> MaterialTheme.colorScheme.outline
        PermissionStatus.DENIED -> MaterialTheme.colorScheme.error
    }

private fun borderWidth(status: PermissionStatus) =
    if (status == PermissionStatus.GRANTED) LikkaComponentSize.borderThin else LikkaComponentSize.borderButton

@LikkaThemePreviews
@Composable
private fun PermissionCardPreview() {
    LikkaPreview {
        Column(
            modifier = Modifier.padding(LikkaSpacing.m),
            verticalArrangement = Arrangement.spacedBy(LikkaSpacing.s),
        ) {
            PermissionCard(
                icon = Icons.Rounded.Layers,
                title = stringResource(R.string.permission_overlay_title),
                description = stringResource(R.string.permission_overlay_description),
                status = PermissionStatus.PENDING,
                onGrantClick = {},
            )
            PermissionCard(
                icon = Icons.Rounded.QueryStats,
                title = stringResource(R.string.permission_usage_title),
                description = stringResource(R.string.permission_usage_description),
                status = PermissionStatus.GRANTED,
                onGrantClick = {},
            )
            PermissionCard(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.permission_notifications_title),
                description = stringResource(R.string.permission_notifications_description),
                status = PermissionStatus.DENIED,
                onGrantClick = {},
            )
        }
    }
}
