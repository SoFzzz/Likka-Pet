package com.likkapet.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.likkapet.domain.model.InstalledApp
import com.likkapet.domain.port.InstalledAppsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Launcher apps visible through the manifest's `MAIN` + `LAUNCHER` `<queries>` (RF-S06); no
 * QUERY_ALL_PACKAGES. Likka-Pet itself is left out.
 */
class PackageManagerInstalledAppsSource(
    context: Context,
) : InstalledAppsSource {
    private val context = context.applicationContext

    override suspend fun launcherApps(): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            queryLauncherActivities(packageManager)
                .filter { it.activityInfo.packageName != context.packageName }
                .distinctBy { it.activityInfo.packageName }
                .map { InstalledApp(it.activityInfo.packageName, appLabel(packageManager, it)) }
        }

    private fun queryLauncherActivities(packageManager: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    // The application label, not the activity's: that is the name users see in the launcher list.
    private fun appLabel(
        packageManager: PackageManager,
        info: ResolveInfo,
    ): String =
        info.activityInfo.applicationInfo
            .loadLabel(packageManager)
            .toString()
}
