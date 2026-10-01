package com.likkapet.data.apps

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap

/**
 * Launcher icons for the watched-app rows and "Añadir app" (RF-S06). Presentation receives [load]
 * as a plain function, so it never depends on this class. Blocking: call it off the main thread.
 */
class PackageManagerAppIconLoader(
    context: Context,
) {
    private val packageManager = context.applicationContext.packageManager
    private val cache = LruCache<String, Bitmap>(CACHE_SIZE)

    fun load(
        packageName: String,
        sizePx: Int,
    ): Bitmap? {
        val key = "$packageName@$sizePx"
        cache.get(key)?.let { return it }
        val icon = decode(packageName, sizePx) ?: return null
        cache.put(key, icon)
        return icon
    }

    // An app uninstalled between listing and drawing simply shows no icon.
    private fun decode(
        packageName: String,
        sizePx: Int,
    ): Bitmap? =
        try {
            packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.i(TAG, "No icon for an app that is no longer installed", e)
            null
        }

    private companion object {
        const val TAG = "AppIconLoader"
        const val CACHE_SIZE = 64
    }
}
