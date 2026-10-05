package com.likkapet.presentation.system

import android.Manifest
import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.likkapet.domain.model.AppPermission
import com.likkapet.domain.model.MiuiTask
import com.likkapet.presentation.permissions.PermissionStatus

/** What the permission and MIUI buttons do; screens get it from [rememberSystemActions]. */
class SystemActions(
    val requestPermission: (AppPermission, PermissionStatus) -> Unit,
    val openMiuiSettings: (MiuiTask) -> Unit,
)

/**
 * Overlay and usage access are special permissions granted in system settings; notifications
 * (API 33+) use the runtime dialog, or the app's notification settings once Android stops showing
 * that dialog (denied twice). [onPermissionResult] runs when the dialog closes; returning from a
 * settings screen is covered by MainActivity's resume refresh.
 */
@Composable
fun rememberSystemActions(onPermissionResult: () -> Unit): SystemActions {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val launcher = remember(context) { SystemSettingsLauncher(context) }
    val notificationDialog =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onPermissionResult() }
    return remember(launcher, activity) {
        SystemActions(
            requestPermission = { permission, status ->
                when (permission) {
                    AppPermission.OVERLAY -> {
                        launcher.openOverlaySettings()
                    }

                    AppPermission.USAGE_STATS -> {
                        launcher.openUsageAccessSettings()
                    }

                    AppPermission.NOTIFICATIONS -> {
                        if (isNotificationDialogBlocked(activity, status)) {
                            launcher.openNotificationSettings()
                        } else {
                            notificationDialog.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            },
            openMiuiSettings = launcher::openMiuiSettings,
        )
    }
}

// After a denial Android shows the rationale flag; once it is false again the dialog no longer appears.
private fun isNotificationDialogBlocked(
    activity: Activity?,
    status: PermissionStatus,
): Boolean =
    status == PermissionStatus.DENIED &&
        activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
