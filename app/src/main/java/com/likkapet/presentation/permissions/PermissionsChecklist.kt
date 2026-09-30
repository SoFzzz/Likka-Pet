package com.likkapet.presentation.permissions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.likkapet.R
import com.likkapet.presentation.components.LikkaPreview
import com.likkapet.presentation.components.LikkaThemePreviews
import com.likkapet.presentation.components.PermissionCard
import com.likkapet.presentation.state.AppPermission
import com.likkapet.presentation.state.PermissionStatus
import com.likkapet.presentation.theme.LikkaSpacing

@Immutable
data class PermissionUi(
    val permission: AppPermission,
    val status: PermissionStatus,
)

/** The permission cards shared by onboarding step 4 and "Revisar permisos" (design system §3.1). */
@Composable
fun PermissionsChecklist(
    permissions: List<PermissionUi>,
    onGrantClick: (AppPermission) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LikkaSpacing.s)) {
        permissions.forEach { item ->
            val copy = copyFor(item.permission)
            PermissionCard(
                icon = copy.icon,
                title = stringResource(copy.titleRes),
                description = stringResource(copy.descriptionRes),
                status = item.status,
                onGrantClick = { onGrantClick(item.permission) },
            )
        }
    }
}

private data class PermissionCopy(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
)

private fun copyFor(permission: AppPermission): PermissionCopy =
    when (permission) {
        AppPermission.OVERLAY -> {
            PermissionCopy(Icons.Rounded.Layers, R.string.permission_overlay_title, R.string.permission_overlay_description)
        }

        AppPermission.USAGE_STATS -> {
            PermissionCopy(Icons.Rounded.QueryStats, R.string.permission_usage_title, R.string.permission_usage_description)
        }

        AppPermission.NOTIFICATIONS -> {
            PermissionCopy(
                Icons.Rounded.Notifications,
                R.string.permission_notifications_title,
                R.string.permission_notifications_description,
            )
        }
    }

@LikkaThemePreviews
@Composable
private fun PermissionsChecklistPreview() {
    LikkaPreview {
        PermissionsChecklist(
            permissions =
                listOf(
                    PermissionUi(AppPermission.OVERLAY, PermissionStatus.GRANTED),
                    PermissionUi(AppPermission.USAGE_STATS, PermissionStatus.PENDING),
                    PermissionUi(AppPermission.NOTIFICATIONS, PermissionStatus.DENIED),
                ),
            onGrantClick = {},
            modifier = Modifier.padding(LikkaSpacing.m),
        )
    }
}
