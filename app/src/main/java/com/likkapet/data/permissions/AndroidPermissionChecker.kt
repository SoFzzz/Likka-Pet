package com.likkapet.data.permissions

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.likkapet.domain.model.AppPermission
import com.likkapet.domain.port.PermissionChecker

/** Real permission checks of RF-A04 (documentación §6, detail of RF-A04). */
class AndroidPermissionChecker(
    context: Context,
) : PermissionChecker {
    private val context = context.applicationContext

    override val requiredPermissions: List<AppPermission> =
        AppPermission.entries.filter { it != AppPermission.NOTIFICATIONS || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }

    override fun isGranted(permission: AppPermission): Boolean =
        when (permission) {
            AppPermission.OVERLAY -> Settings.canDrawOverlays(context)
            AppPermission.USAGE_STATS -> hasUsageAccess()
            AppPermission.NOTIFICATIONS -> hasNotificationPermission()
        }

    // unsafeCheckOpNoThrow is the only path with minSdk 29. MODE_DEFAULT means "decided by the
    // permission itself", which some ROMs report until the user touches the switch.
    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT -> isPermissionGranted(Manifest.permission.PACKAGE_USAGE_STATS)
            else -> false
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)

    private fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
