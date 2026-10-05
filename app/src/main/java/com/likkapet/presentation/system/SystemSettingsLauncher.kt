package com.likkapet.presentation.system

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.likkapet.domain.model.MiuiTask

/**
 * Opens the system screens where the user grants what Likka needs (RF-A04, RF-A05). Each target
 * lists candidate intents from most to least specific; the first one the system accepts wins, and
 * the app's own details screen is the last resort. MIUI screens are not public API and change
 * between versions, so they are always tried this way (documentación §6, detail of RF-A05).
 */
class SystemSettingsLauncher(
    private val context: Context,
) {
    private val packageUri: Uri = Uri.fromParts(PACKAGE_SCHEME, context.packageName, null)

    fun openOverlaySettings() =
        launchFirst(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        )

    fun openUsageAccessSettings() =
        launchFirst(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        )

    fun openNotificationSettings() =
        launchFirst(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))

    fun openMiuiSettings(task: MiuiTask) =
        when (task) {
            MiuiTask.AUTOSTART -> {
                launchFirst(miuiComponent(SECURITY_CENTER, AUTOSTART_ACTIVITY))
            }

            MiuiTask.BATTERY -> {
                launchFirst(miuiBatteryIntent())
            }

            MiuiTask.POPUP -> {
                launchFirst(
                    miuiPermissionEditor(PERMISSIONS_EDITOR_ACTIVITY),
                    miuiPermissionEditor(APP_PERMISSIONS_EDITOR_ACTIVITY),
                )
            }
        }

    private fun miuiComponent(
        packageName: String,
        className: String,
    ) = Intent().setComponent(ComponentName(packageName, className))

    private fun miuiBatteryIntent() =
        miuiComponent(POWER_KEEPER, BATTERY_ACTIVITY)
            .putExtra(EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(EXTRA_PACKAGE_LABEL, context.applicationInfo.loadLabel(context.packageManager).toString())

    private fun miuiPermissionEditor(className: String) =
        Intent(MIUI_PERMISSION_EDITOR_ACTION)
            .setClassName(SECURITY_CENTER, className)
            .putExtra(EXTRA_MIUI_PACKAGE, context.packageName)

    private fun launchFirst(vararg candidates: Intent) {
        val opened = (candidates.asList() + appDetailsIntent()).firstOrNull(::tryStart)
        Log.i(TAG, "Opened settings: ${opened?.describe() ?: "none"}")
    }

    private fun appDetailsIntent() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)

    // A missing activity (other MIUI version, not a Xiaomi) or a non-exported one just moves on to
    // the next candidate.
    private fun tryStart(intent: Intent): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.i(TAG, "Not available: ${intent.describe()}", e)
            false
        } catch (e: SecurityException) {
            Log.i(TAG, "Not allowed: ${intent.describe()}", e)
            false
        }

    private fun Intent.describe(): String = component?.flattenToShortString() ?: action.orEmpty()

    private companion object {
        const val TAG = "SystemSettings"
        const val PACKAGE_SCHEME = "package"

        const val SECURITY_CENTER = "com.miui.securitycenter"
        const val AUTOSTART_ACTIVITY = "com.miui.permcenter.autostart.AutoStartManagementActivity"
        const val PERMISSIONS_EDITOR_ACTIVITY = "com.miui.permcenter.permissions.PermissionsEditorActivity"
        const val APP_PERMISSIONS_EDITOR_ACTIVITY = "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
        const val MIUI_PERMISSION_EDITOR_ACTION = "miui.intent.action.APP_PERM_EDITOR"
        const val EXTRA_MIUI_PACKAGE = "extra_pkgname"

        const val POWER_KEEPER = "com.miui.powerkeeper"
        const val BATTERY_ACTIVITY = "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_PACKAGE_LABEL = "package_label"
    }
}
