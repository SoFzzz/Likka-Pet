package com.likkapet.service

import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.likkapet.domain.model.ThemeMode
import com.likkapet.presentation.theme.LikkaTheme

/**
 * The lifecycle, ViewModel store and saved-state registry a [ComposeView] needs outside an
 * Activity (documentación §9.1); one object implements the three. Main thread only.
 */
class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    init {
        // Must happen before moving to CREATED.
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /**
     * RESUMED while the window is visible; CREATED while it is only hidden (SUSPENDED or away), which
     * also pauses Compose's frame clock, so a hidden Likka costs no frames.
     */
    fun setVisible(isVisible: Boolean) {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        lifecycleRegistry.currentState = if (isVisible) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/** The window's root [view] (the drag layout around the [ComposeView]) and the lifecycle it runs on. */
class OverlayViewHolder(
    val view: OverlayDragLayout,
    val owner: OverlayLifecycleOwner,
)

/** Creates and destroys the overlay's single [ComposeView] (documentación §9.1, RF-O05). */
object ComposeOverlayHelper {
    fun create(
        context: Context,
        dragListener: OverlayDragListener,
        content: @Composable () -> Unit,
    ): OverlayViewHolder {
        val owner = OverlayLifecycleOwner()
        val composeView =
            ComposeView(context).apply {
                // Always dark: the overlay floats over other apps, regardless of theme_mode (RF-S08, design system §1.1).
                setContent { LikkaTheme(ThemeMode.DARK) { content() } }
            }
        val root =
            OverlayDragLayout(context, dragListener).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                addView(composeView)
            }
        return OverlayViewHolder(root, owner)
    }

    /**
     * Removes the view first (which disposes its composition), then destroys its lifecycle;
     * removeViewImmediate, so the view is really detached before the lifecycle ends.
     */
    fun destroy(
        windowManager: WindowManager,
        holder: OverlayViewHolder,
    ) {
        try {
            windowManager.removeViewImmediate(holder.view)
        } catch (e: IllegalArgumentException) {
            // The view was already detached: the documented exception to "no empty catch blocks".
        }
        holder.owner.destroy()
    }
}
