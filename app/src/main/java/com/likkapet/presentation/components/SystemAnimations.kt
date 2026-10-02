package com.likkapet.presentation.components

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * The system's *Quitar animaciones* (`ANIMATOR_DURATION_SCALE == 0`, RF-O14, RNF-U05): with it on,
 * sprites show first frames only, nothing shakes, slides or pulses, and Likka stays where it is.
 */
object SystemAnimations {
    fun areEnabled(resolver: ContentResolver): Boolean =
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

    /** Calls [onChanged] on the main thread whenever the setting changes, until the result is closed. */
    fun observe(
        resolver: ContentResolver,
        onChanged: (areEnabled: Boolean) -> Unit,
    ): AutoCloseable {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = onChanged(areEnabled(resolver))
            }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        return AutoCloseable { resolver.unregisterContentObserver(observer) }
    }
}

/** [SystemAnimations.areEnabled], kept up to date while in composition; always true in previews. */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return true
    val resolver = LocalContext.current.contentResolver
    var areEnabled by remember(resolver) { mutableStateOf(SystemAnimations.areEnabled(resolver)) }
    DisposableEffect(resolver) {
        val observation = SystemAnimations.observe(resolver) { areEnabled = it }
        onDispose { observation.close() }
    }
    return areEnabled
}
