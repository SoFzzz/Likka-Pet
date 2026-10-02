package com.likkapet.presentation.components

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONException
import java.io.IOException

/**
 * The sheet's JSON data together with its decoded `likka.png`, and where the feet are in each frame
 * (for the overlay's foot shadow, design system §1.7), read once from the image's alpha.
 */
class LoadedSpriteSheet(
    val data: LikkaSpriteSheet,
    val image: ImageBitmap,
    val feetSpans: Map<SpriteFrame, IntRange>,
)

/**
 * Reads `assets/sprites/` (likka.png, likka.json, likka_poses.json) once per process. The folder is
 * local and not versioned (documentación §10.2), so a missing or broken sheet is logged and
 * reported as null: callers then draw the placeholder instead of crashing.
 */
object LikkaSpriteAssets {
    private const val TAG = "LikkaSpriteAssets"
    private const val DIR = "sprites"

    @Volatile
    private var loaded: LoadedSpriteSheet? = null

    @Volatile
    private var hasFailed = false

    fun cachedOrNull(): LoadedSpriteSheet? = loaded

    /** Blocking (asset I/O and PNG decoding): call it off the main thread. */
    @Synchronized
    fun load(assets: AssetManager): LoadedSpriteSheet? {
        if (loaded != null || hasFailed) return loaded
        loaded =
            try {
                val data = LikkaSpriteSheet.parse(assets.readText("likka.json"), assets.readText("likka_poses.json"))
                val bitmap = assets.readPixelArt("likka.png")
                LoadedSpriteSheet(data, bitmap.asImageBitmap(), feetSpansOf(data.frames, bitmap))
            } catch (e: IOException) {
                Log.w(TAG, "Sprite sheet not available in assets/$DIR; drawing the placeholder", e)
                null
            } catch (e: JSONException) {
                Log.w(TAG, "Sprite sheet JSON in assets/$DIR is malformed; drawing the placeholder", e)
                null
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Sprite sheet in assets/$DIR is invalid; drawing the placeholder", e)
                null
            }
        hasFailed = loaded == null
        return loaded
    }

    private fun AssetManager.readText(name: String): String = open("$DIR/$name").bufferedReader().use { it.readText() }

    // Binary alpha (design system §1.7), so any non-zero alpha is part of Likka.
    private fun feetSpansOf(
        frames: List<SpriteFrame>,
        bitmap: Bitmap,
    ): Map<SpriteFrame, IntRange> =
        frames
            .mapNotNull { frame ->
                SpriteExtras.feetSpan { x, y -> Color.alpha(bitmap.getPixel(frame.x + x, frame.y + y)) != 0 }?.let { frame to it }
            }.toMap()

    private fun AssetManager.readPixelArt(name: String): Bitmap {
        // No density scaling: one sheet pixel must stay one bitmap pixel for integer scaling (§1.7).
        val options =
            BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        val bitmap =
            open("$DIR/$name").use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IllegalArgumentException("assets/$DIR/$name is not a readable image")
        return bitmap
    }
}
