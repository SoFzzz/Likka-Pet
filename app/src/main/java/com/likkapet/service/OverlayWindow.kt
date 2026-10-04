package com.likkapet.service

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.core.view.OneShotPreDrawListener
import com.likkapet.domain.model.OverlayInsets

/** The display size and the system bars and cutout as insets, in physical pixels (RF-O13). */
data class ScreenBounds(
    val widthPx: Int,
    val heightPx: Int,
    val insets: OverlayInsets,
)

/** Where the window goes and how big it is; [WindowManager.LayoutParams.WRAP_CONTENT] sizes it to its content. */
data class WindowPlacement(
    val x: Int,
    val y: Int,
    val widthPx: Int,
    val heightPx: Int,
    val isVisible: Boolean,
)

/**
 * The overlay's one window (RF-O01, RF-O05): added once with [WindowManager.addView], then only
 * moved, resized, hidden and shown with [WindowManager.updateViewLayout] until [remove]. Hidden means
 * invisible and untouchable, with the composition kept and its frame clock paused. Main thread only.
 */
class OverlayWindow(
    private val context: Context,
    private val dragListener: OverlayDragListener,
    private val log: (String) -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var holder: OverlayViewHolder? = null
    private val params = baseLayoutParams()
    private var appliedPlacement: WindowPlacement? = null

    // A window the system refused is not asked for again until the scene changes ([allowRetry]) or
    // the window is removed, instead of once per state, i.e. every second at Level 3.
    private var wasRefused = false

    val exists: Boolean get() = holder != null

    /** Creates the window on first use (with [content]), then applies [placement]. */
    fun show(
        placement: WindowPlacement,
        content: @Composable () -> Unit,
    ) {
        val current = holder ?: create(content) ?: return
        if (placement != appliedPlacement) {
            params.x = placement.x
            params.y = placement.y
            params.width = placement.widthPx
            params.height = placement.heightPx
            setTouchable(placement.isVisible)
            windowManager.updateViewLayout(current.view, params)
            appliedPlacement = placement
        }
        current.view.visibility = if (placement.isVisible) View.VISIBLE else View.INVISIBLE
        current.owner.setVisible(placement.isVisible)
    }

    /** Keeps the window and its state for a quick return (SUSPENDED, away); no-op without a window. */
    fun hide() {
        val current = holder ?: return
        if (appliedPlacement?.isVisible == false) return
        setTouchable(false)
        windowManager.updateViewLayout(current.view, params)
        appliedPlacement = appliedPlacement?.copy(isVisible = false)
        current.view.visibility = View.INVISIBLE
        current.owner.setVisible(false)
    }

    /** Whether a drag moves the window (Levels 1–2 only, RF-O11); no-op without a window. */
    fun setDragEnabled(isEnabled: Boolean) {
        holder?.view?.isDragEnabled = isEnabled
    }

    /** A new scene (another kind or level): a refused window may be asked for again. */
    fun allowRetry() {
        wasRefused = false
    }

    fun remove() {
        wasRefused = false
        val current = holder ?: return
        holder = null
        ComposeOverlayHelper.destroy(windowManager, current)
    }

    /** Runs [action] right before the window's next frame is drawn (RNF-R03 timing). */
    fun doBeforeNextFrame(action: () -> Unit) {
        val view = holder?.view ?: return
        OneShotPreDrawListener.add(view, action)
    }

    fun screenBounds(): ScreenBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) currentScreenBounds() else legacyScreenBounds()

    // Overlay permission revoked while the service runs: addView would throw, so nothing is shown.
    private fun create(content: @Composable () -> Unit): OverlayViewHolder? {
        if (wasRefused) return null
        if (!Settings.canDrawOverlays(context)) {
            wasRefused = true
            log("Overlay not shown: no permission to draw over other apps")
            return null
        }
        val created = ComposeOverlayHelper.create(context, dragListener, content)
        setTouchable(false)
        created.view.visibility = View.INVISIBLE
        try {
            windowManager.addView(created.view, params)
        } catch (e: WindowManager.BadTokenException) {
            return discard(created, e)
        } catch (e: SecurityException) {
            return discard(created, e)
        }
        appliedPlacement = null
        holder = created
        return created
    }

    // The permission can go between the check and addView, or a ROM can refuse the window: no overlay
    // this time, a later state tries again.
    private fun discard(
        created: OverlayViewHolder,
        e: RuntimeException,
    ): OverlayViewHolder? {
        log("Overlay not shown: window refused (${e::class.simpleName})")
        wasRefused = true
        created.owner.destroy()
        return null
    }

    private fun setTouchable(isTouchable: Boolean) {
        params.flags =
            if (isTouchable) {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
    }

    private fun currentScreenBounds(): ScreenBounds {
        val metrics = windowManager.currentWindowMetrics
        val bars =
            metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        return ScreenBounds(
            widthPx = metrics.bounds.width(),
            heightPx = metrics.bounds.height(),
            insets = OverlayInsets(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom),
        )
    }

    // API 29 has no WindowMetrics: the real display size, and the bar heights the system declares.
    @Suppress("DEPRECATION", "DiscouragedApi")
    private fun legacyScreenBounds(): ScreenBounds {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val resources = context.resources

        fun systemDimen(name: String): Int {
            val id = resources.getIdentifier(name, "dimen", "android")
            return if (id == 0) 0 else resources.getDimensionPixelSize(id)
        }
        val isPortrait = metrics.heightPixels >= metrics.widthPixels
        val navigationBar = systemDimen(if (isPortrait) "navigation_bar_height" else "navigation_bar_width")
        return ScreenBounds(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            insets =
                OverlayInsets(
                    left = 0,
                    top = systemDimen("status_bar_height"),
                    right = if (isPortrait) 0 else navigationBar,
                    bottom = if (isPortrait) navigationBar else 0,
                ),
        )
    }

    private fun baseLayoutParams() =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // RF-O01: the app underneath keeps the keyboard, Back and every touch outside the window.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                // x/y are the window's top-left corner in screen pixels, the frame OverlayMotionPlanner uses.
                gravity = Gravity.TOP or Gravity.START
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                // API 30+ would otherwise keep fitting the window inside the system bars, shifting
                // that frame; the planner already keeps every position out of the insets (RF-O13).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    fitInsetsTypes = 0
                    fitInsetsSides = 0
                }
            }

    companion object {
        private const val TAG = "LikkaOverlay"

        /**
         * A context for overlay windows: on API 30+ a window context for TYPE_APPLICATION_OVERLAY, so
         * the window and the screen metrics come from a visual context; on API 29, [base] itself.
         */
        fun overlayContext(base: Context): Context {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return base
            val display = base.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            if (display == null) {
                Log.w(TAG, "No default display; using the service context for the overlay")
                return base
            }
            val displayContext = base.createDisplayContext(display)
            val localizedDisplay = displayContext.createConfigurationContext(base.resources.configuration)
            return localizedDisplay.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        }
    }
}
