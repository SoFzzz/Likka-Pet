package com.likkapet.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import com.likkapet.presentation.theme.LikkaComponentSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads an app's launcher icon at a pixel size, or null; injected from LikkaApplication (blocking). */
typealias AppIconLoader = (packageName: String, sizePx: Int) -> Bitmap?

/** Previews and tests: no icons, the space stays reserved. */
val NoAppIcons: AppIconLoader = { _, _ -> null }

/**
 * Launcher icon of an app (design system §2.5), `LikkaComponentSize.appIcon`. Decorative: the app
 * name is always next to it, so it has no content description. Loaded off the main thread.
 */
@Composable
fun AppIcon(
    packageName: String,
    loader: AppIconLoader,
    modifier: Modifier = Modifier,
) {
    val sizePx = with(LocalDensity.current) { LikkaComponentSize.appIcon.roundToPx() }
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName, sizePx) {
        value = withContext(Dispatchers.IO) { loader(packageName, sizePx)?.asImageBitmap() }
    }
    Box(modifier = modifier.size(LikkaComponentSize.appIcon)) {
        icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(LikkaComponentSize.appIcon)) }
    }
}
