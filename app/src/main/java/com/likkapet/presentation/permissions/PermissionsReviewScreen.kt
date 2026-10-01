package com.likkapet.presentation.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.likkapet.R
import com.likkapet.domain.model.AppPermission
import com.likkapet.presentation.components.LikkaButton
import com.likkapet.presentation.components.LikkaPose
import com.likkapet.presentation.components.LikkaPreview
import com.likkapet.presentation.components.LikkaStage
import com.likkapet.presentation.components.LikkaThemePreviews
import com.likkapet.presentation.components.LikkaTopBar
import com.likkapet.presentation.components.screenInsets
import com.likkapet.presentation.system.rememberSystemActions
import com.likkapet.presentation.theme.LikkaSpacing
import com.likkapet.presentation.theme.LikkaSpriteSize
import kotlinx.coroutines.flow.StateFlow

/** Real permission state from [PermissionMonitor], refreshed by MainActivity on every resume. */
class PermissionsViewModel(
    private val permissionMonitor: PermissionMonitor,
) : ViewModel() {
    val permissions: StateFlow<List<PermissionUi>> = permissionMonitor.permissions

    /** The screen opens the system screen or dialog; this only remembers the attempt. */
    fun onGrantPermissionClick(permission: AppPermission) = permissionMonitor.markRequested(permission)

    fun onPermissionResult() = permissionMonitor.refresh()
}

/**
 * "Revisar permisos" (reuses onboarding step 4, design system §3.1). With [isGate] it is the
 * screen shown on open when a permission was revoked (RF-A06): no back arrow, and a Continue
 * button that appears once everything is granted again.
 */
@Composable
fun PermissionsReviewScreen(
    viewModel: PermissionsViewModel,
    isGate: Boolean,
    onBackClick: () -> Unit,
    onContinueClick: () -> Unit,
) {
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val systemActions = rememberSystemActions(onPermissionResult = viewModel::onPermissionResult)
    PermissionsReviewContent(
        permissions = permissions,
        isGate = isGate,
        onGrantClick = { permission ->
            val status = permissions.first { it.permission == permission }.status
            viewModel.onGrantPermissionClick(permission)
            systemActions.requestPermission(permission, status)
        },
        onBackClick = onBackClick,
        onContinueClick = onContinueClick,
    )
}

@Composable
fun PermissionsReviewContent(
    permissions: List<PermissionUi>,
    isGate: Boolean,
    onGrantClick: (AppPermission) -> Unit,
    onBackClick: () -> Unit,
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasAll = permissions.all { it.status == PermissionStatus.GRANTED }
    val hasDenied = permissions.any { it.status == PermissionStatus.DENIED }
    Column(modifier = modifier.screenInsets()) {
        LikkaTopBar(
            title = stringResource(R.string.permissions_review_title),
            onBackClick = onBackClick.takeIf { !isGate },
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (hasDenied || (isGate && !hasAll)) {
                LikkaStage(pose = LikkaPose.WORRIED, size = LikkaSpriteSize.onboarding)
            }
            Text(
                text = stringResource(if (hasAll) R.string.permissions_review_all_granted else R.string.permissions_review_missing),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth().padding(vertical = LikkaSpacing.m),
            )
            PermissionsChecklist(permissions = permissions, onGrantClick = onGrantClick)
        }
        if (isGate) {
            LikkaButton(
                text = stringResource(R.string.onboarding_next),
                onClick = onContinueClick,
                enabled = hasAll,
                disabledReason = stringResource(R.string.onboarding_permissions_required),
                modifier = Modifier.padding(vertical = LikkaSpacing.m),
            )
        }
    }
}

private val previewPermissions =
    listOf(
        PermissionUi(AppPermission.OVERLAY, PermissionStatus.GRANTED),
        PermissionUi(AppPermission.USAGE_STATS, PermissionStatus.DENIED),
        PermissionUi(AppPermission.NOTIFICATIONS, PermissionStatus.GRANTED),
    )

@LikkaThemePreviews
@Composable
private fun PermissionsReviewContentPreview() {
    LikkaPreview {
        PermissionsReviewContent(previewPermissions, isGate = false, onGrantClick = {}, onBackClick = {}, onContinueClick = {})
    }
}

@LikkaThemePreviews
@Composable
private fun PermissionsGatePreview() {
    LikkaPreview {
        PermissionsReviewContent(previewPermissions, isGate = true, onGrantClick = {}, onBackClick = {}, onContinueClick = {})
    }
}
