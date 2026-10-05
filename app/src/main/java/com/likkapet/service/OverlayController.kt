package com.likkapet.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.LocalReactionDisplay
import com.likkapet.domain.OverlayMotionPlanner
import com.likkapet.domain.OverlaySceneReducer
import com.likkapet.domain.model.LikkaOverlayState
import com.likkapet.domain.model.OverlayFeed
import com.likkapet.domain.model.OverlayGeometry
import com.likkapet.domain.model.OverlayMotion
import com.likkapet.domain.model.OverlayScene
import com.likkapet.presentation.components.SystemAnimations
import com.likkapet.presentation.overlay.OverlayActions
import com.likkapet.presentation.overlay.OverlayContent
import com.likkapet.presentation.overlay.OverlayEffects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Puts the session's state on screen (documentación §5, §9.1): unidirectional, an [OverlayFeed]
 * (the coordinator's [LikkaOverlayState] with its roast, and its global state) comes in, [OverlaySceneReducer] turns them and
 * the [OverlayMotionPlanner]'s position into an [OverlayScene], and [OverlayWindow] shows it. The
 * composable only sends events back ([onSurrenderClick], [openApp], taps, its measured size), and
 * the window's root reports drags.
 *
 * Movement (§9.6, RF-O09–RF-O14): while Levels 1–2 are on screen the planner is ticked whenever its
 * next change is due (every step of [EscalationConfig.MOVEMENT_STEP_FPS] while walking) and each new
 * position is applied with `updateViewLayout`, so Likka hops, walks and walks back; nothing ticks
 * while hidden, at Level 3, in a rest or with *Quitar animaciones* on, which the planner is told
 * about as it changes. A drag moves the window
 * under the finger through the planner, which keeps it out of the system bars; local reactions
 * (RF-O12) show in the bubble without any network.
 *
 * Leaving (RF-O03): on an ejection the launcher is opened first, while the Level 3 panel is still
 * visible (Android 15 lets the app start it only then), and the window is removed after.
 */
class OverlayController(
    private val context: Context,
    private val feeds: Flow<OverlayFeed>,
    private val planner: OverlayMotionPlanner,
    private val reactions: LocalReactionDisplay,
    private val onSurrenderClick: () -> Unit,
    private val openApp: () -> Unit,
    private val log: (String) -> Unit,
) {
    private val dragListener =
        object : OverlayDragListener {
            override fun onTouchDown() = this@OverlayController.onTouchDown()

            override fun onDragStarted() = this@OverlayController.onDragStarted()

            override fun onDragMoved(
                dxPx: Int,
                dyPx: Int,
            ) = this@OverlayController.onDragMoved(dxPx, dyPx)

            override fun onDragEnded(
                dxPx: Int,
                dyPx: Int,
            ) = this@OverlayController.onDragEnded(dxPx, dyPx)
        }
    private val window = OverlayWindow(context, dragListener, log)
    private val homeLauncher = HomeLauncher(context)
    private val alerts = OverlayAlerts(context, log)
    private val reducer = OverlaySceneReducer()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val expireReaction = Runnable { render() }

    private var feed = OverlayFeed.INITIAL
    private var scene: OverlayScene = OverlayScene.Withdrawn
    private var screen: ScreenBounds = window.screenBounds()
    private var lastMeasure: Measure? = null
    private var lastLoggedScreen: ScreenBounds? = null
    private var lastLoggedVisible: String? = null
    private var isStopped = false
    private var areAnimationsEnabled = true
    private var animationsObservation: AutoCloseable? = null
    private var runScope: CoroutineScope? = null
    private var ticker: Job? = null

    // Where the window and Likka inside it were when the finger went down, and the drag's last
    // distance from there; dragStart is null when no drag is under way.
    private var touchDown: DragAnchor? = null
    private var dragStart: DragAnchor? = null
    private var lastDragDelta = IntOffset.Zero
    private var likkaOffset = IntOffset.Zero
    private var shakeId = NO_SHAKE

    // What the composition draws: the last visible scene, kept while the window is only hidden.
    private var drawnScene by mutableStateOf<OverlayScene>(OverlayScene.Withdrawn)
    private var drawnEffects by mutableStateOf(OverlayEffects.NONE)

    private val actions =
        OverlayActions(
            onSurrenderClick = {
                log("Surrender clicked")
                onSurrenderClick()
            },
            onOpenApp = {
                log("Open app (long press)")
                openApp()
            },
            onTap = ::onTap,
            onMeasured = ::onMeasured,
            onLikkaPlaced = ::onLikkaPlaced,
        )

    private val content: @Composable () -> Unit = { OverlayContent(drawnScene, drawnEffects, actions) }

    /**
     * Renders every feed until cancelled. Main thread only, like the constructor: the window, its
     * context and the screen bounds belong to it.
     */
    suspend fun run() {
        // If rendering ever fails, the window must not stay on screen frozen (a stuck, touchable
        // Level 3 panel): it goes with the collector.
        try {
            coroutineScope {
                runScope = this
                watchSystemAnimations()
                feeds.collect(::onFeed)
            }
        } finally {
            stop()
        }
    }

    /**
     * Rotation or a display change: new screen bounds and insets for the planner and the panel. The
     * window context may receive the new configuration after the service does, so the bounds are
     * read again on the next main-loop turn as well. Main thread.
     */
    fun onConfigurationChanged() {
        if (isStopped) return
        refreshScreen()
        mainHandler.post { if (!isStopped) refreshScreen() }
    }

    /** Removes the window for good (the service is ending). Main thread. */
    fun stop() {
        isStopped = true
        ticker?.cancel()
        ticker = null
        runScope = null
        animationsObservation?.close()
        animationsObservation = null
        mainHandler.removeCallbacksAndMessages(null)
        window.remove()
    }

    // RF-O14: *Quitar animaciones* freezes the movement, read now and whenever it changes.
    private fun watchSystemAnimations() {
        val resolver = context.contentResolver
        onAnimationsSetting(SystemAnimations.areEnabled(resolver))
        animationsObservation = SystemAnimations.observe(resolver, ::onAnimationsSetting)
    }

    private fun onAnimationsSetting(areEnabled: Boolean) {
        if (isStopped) return
        if (areEnabled != areAnimationsEnabled) log("System animations ${if (areEnabled) "on" else "off"}")
        areAnimationsEnabled = areEnabled
        planner.onAnimationsEnabledChanged(areEnabled)
        render()
    }

    private fun onFeed(next: OverlayFeed) {
        if (isStopped) return
        feed = next
        planner.onOverlayStateChanged(next.overlay)
        render()
    }

    private fun refreshScreen() {
        screen = window.screenBounds()
        lastMeasure?.let(::sendGeometry)
        render()
    }

    // Called from inside a Compose layout pass: moving the window there would relayout mid-pass, so
    // the geometry is applied on the next main-loop turn.
    private fun onMeasured(
        size: IntSize,
        spritePixelPx: Int,
    ) {
        if (size.width <= 0 || size.height <= 0) return
        mainHandler.post {
            if (isStopped) return@post
            val measure = Measure(size, spritePixelPx)
            lastMeasure = measure
            sendGeometry(measure)
            render()
        }
    }

    // The planner ignores a geometry equal to the one it has, so this is cheap to repeat.
    private fun sendGeometry(measure: Measure) {
        if (!needsGeometry(scene)) return
        // Read again every time: the bounds kept from the last rotation callback can be the other orientation's.
        screen = window.screenBounds()
        if (screen != lastLoggedScreen) {
            lastLoggedScreen = screen
            log("Screen ${screen.widthPx}x${screen.heightPx} insets=${screen.insets}")
        }
        planner.onGeometryChanged(
            OverlayGeometry(
                screenWidthPx = screen.widthPx,
                screenHeightPx = screen.heightPx,
                insets = screen.insets,
                windowWidthPx = measure.size.width,
                windowHeightPx = measure.size.height,
                spritePixelPx = measure.spritePixelPx,
            ),
        )
    }

    private fun render(isFromTicker: Boolean = false) {
        if (isStopped) return
        val previous = scene
        var next = reducer.sceneFor(feed.likkaState, feed.overlay, planner.motion.value)
        if (needsGeometry(next) && !needsGeometry(previous)) {
            // A rotation while Likka was hidden or gone reached the planner with nothing to refit:
            // the window is already measured, so no new size will come to refresh the geometry.
            scene = next
            lastMeasure?.let(::sendGeometry)
            next = reducer.sceneFor(feed.likkaState, feed.overlay, planner.motion.value)
        }
        scene = next
        if (sceneKey(next) != sceneKey(previous)) window.allowRetry()
        val isMovable = next is OverlayScene.Companion && next.motion != null
        if (!isMovable) endDragUnderWay()
        when (next) {
            OverlayScene.Withdrawn -> withdraw()
            is OverlayScene.Ejected -> eject(previous, next)
            OverlayScene.Hidden -> window.hide()
            is OverlayScene.Companion -> showBesideLikka(next, next.motion, next.level)
            is OverlayScene.Resting -> showBesideLikka(next, next.motion, level = null)
            is OverlayScene.Fury -> showPanel(next)
        }
        window.setDragEnabled(isMovable)
        updateTicker(isMoving = isMovable && areAnimationsEnabled, isFromTicker = isFromTicker)
    }

    // Levels 1–2 on screen: the planner is ticked when its next change is due (each walking step at
    // MOVEMENT_STEP_FPS, the next hop or phase end otherwise, §9.6), and not at all while nothing can
    // change on its own. Any other render may have moved that time, so it plans the wait again.
    private fun updateTicker(
        isMoving: Boolean,
        isFromTicker: Boolean,
    ) {
        if (!isMoving) {
            ticker?.cancel()
            ticker = null
            return
        }
        if (isFromTicker && ticker?.isActive == true) return
        ticker?.cancel()
        ticker =
            runScope?.launch {
                while (isActive) {
                    val waitMs = planner.msUntilNextChange() ?: return@launch
                    delay(waitMs.coerceAtLeast(MIN_TICK_WAIT_MS))
                    planner.onTick()
                    render(isFromTicker = true)
                }
            }
    }

    private fun needsGeometry(shown: OverlayScene): Boolean = shown is OverlayScene.Companion || shown is OverlayScene.Resting

    private fun withdraw() {
        alerts.reset()
        shakeId = NO_SHAKE
        lastLoggedVisible = null
        window.remove()
    }

    // RF-O03: home first, while the panel is still visible, then the window goes. Only when the
    // watched app is still in front ("Me rindo", the end of the countdown): a user who already left
    // for another app is not pulled out of it. Only with the panel really on screen, too: without a
    // visible window Android 15 would block the launch anyway. A repeated Ejected (the state stays
    // EJECTED up to 5 min) does nothing more.
    private fun eject(
        previous: OverlayScene,
        ejected: OverlayScene.Ejected,
    ) {
        if (ejected.sendsHome && previous is OverlayScene.Fury && window.exists) {
            log("Ejected: opening the launcher, then removing the overlay")
            homeLauncher.launch()
        }
        withdraw()
    }

    // Levels 1–2 and the rests: the window wraps Likka and the bubble, at the planner's position.
    // Until the planner has placed it (it needs the measured window first), it stays invisible.
    private fun showBesideLikka(
        shown: OverlayScene,
        motion: OverlayMotion?,
        level: Int?,
    ) {
        drawnScene = shown
        drawnEffects = OverlayEffects(reaction = reactions.current().takeIf { level != null }, shakeId = shakeId)
        scheduleReactionExpiry()
        val isPlaced = motion != null
        window.show(
            WindowPlacement(
                x = motion?.x ?: 0,
                y = motion?.y ?: 0,
                widthPx = WindowManager.LayoutParams.WRAP_CONTENT,
                heightPx = WindowManager.LayoutParams.WRAP_CONTENT,
                isVisible = isPlaced,
            ),
            content,
        )
        if (isPlaced) onVisible(shown, level)
    }

    // Level 3: the whole screen between the system bars, in portrait and landscape (owner decision,
    // 2026-10-05; it replaces the 80% panel of §1.8), never over them (RF-O13).
    private fun showPanel(fury: OverlayScene.Fury) {
        drawnScene = fury
        drawnEffects = OverlayEffects.NONE
        val insets = screen.insets
        window.show(
            WindowPlacement(
                x = insets.left,
                y = insets.top,
                widthPx = screen.widthPx - insets.left - insets.right,
                heightPx = screen.heightPx - insets.top - insets.bottom,
                isVisible = true,
            ),
            content,
        )
        onVisible(fury, FURY_LEVEL)
    }

    // RNF-R03: one line when a new kind or level becomes visible, logged right before its frame is drawn.
    // The Level 2 vibration also starts its 1-sprite-pixel shake (design system §1.6).
    private fun onVisible(
        shown: OverlayScene,
        level: Int?,
    ) {
        val hasVibrated = level?.let(alerts::onLevelShown) ?: false
        if (hasVibrated && level == SHAKE_LEVEL) {
            shakeId++
            drawnEffects = drawnEffects.copy(shakeId = shakeId)
        }
        val label = "${labelOf(shown)} level=${level ?: 0}"
        if (label == lastLoggedVisible) return
        lastLoggedVisible = label
        window.doBeforeNextFrame { log("Overlay visible: $label") }
    }

    private fun onTap() {
        if (isStopped || scene !is OverlayScene.Companion) return
        reactions.onTap()
        render()
    }

    // The window can still hop or step between the finger going down and the drag starting, so the
    // drag is measured from where things were at the touch.
    private fun onTouchDown() {
        touchDown = planner.motion.value?.let { DragAnchor(IntOffset(it.x, it.y), likkaOffset) }
    }

    private fun onDragStarted() {
        if (isStopped || scene !is OverlayScene.Companion) return
        val start = touchDown ?: return
        dragStart = start
        lastDragDelta = IntOffset.Zero
        planner.onDragStarted()
        reactions.onDragStarted()
        log("Drag started")
        render()
    }

    private fun onDragMoved(
        dxPx: Int,
        dyPx: Int,
    ) {
        if (dragStart == null) return
        lastDragDelta = IntOffset(dxPx, dyPx)
        followFinger()
        render()
    }

    private fun onDragEnded(
        dxPx: Int,
        dyPx: Int,
    ) {
        val start = dragStart ?: return
        dragStart = null
        val corner = windowCornerFor(start, IntOffset(dxPx, dyPx))
        planner.onDragEnded(corner.x, corner.y)
        reactions.onDragEnded()
        log("Drag ended")
        render()
    }

    // Likka moved inside the window (the bubble grew with the drag phrase, or changed side): the
    // window moves the other way, so Likka itself stays under the finger. Reported from a layout
    // pass, so applied on the next main-loop turn, like the measured size.
    private fun onLikkaPlaced(offsetInWindow: IntOffset) {
        mainHandler.post {
            if (isStopped || offsetInWindow == likkaOffset) return@post
            likkaOffset = offsetInWindow
            if (dragStart == null) return@post
            followFinger()
            render()
        }
    }

    private fun followFinger() {
        val start = dragStart ?: return
        val corner = windowCornerFor(start, lastDragDelta)
        planner.onDragMoved(corner.x, corner.y)
    }

    private fun windowCornerFor(
        start: DragAnchor,
        fingerDelta: IntOffset,
    ): IntOffset = start.windowCorner + fingerDelta - (likkaOffset - start.likkaOffset)

    // The scene stopped being draggable mid-drag (hidden, another level, gone): the window is dropped
    // where it is, so the planner never stays waiting for a finger that will not come back.
    private fun endDragUnderWay() {
        if (dragStart == null) return
        dragStart = null
        planner.motion.value?.let { planner.onDragEnded(it.x, it.y) }
        reactions.onDragEnded()
    }

    private fun scheduleReactionExpiry() {
        mainHandler.removeCallbacks(expireReaction)
        reactions.msUntilExpiry()?.let { mainHandler.postDelayed(expireReaction, it) }
    }

    // Kind and level, ignoring the roast, countdown and position, which change within one scene.
    private fun sceneKey(of: OverlayScene): String =
        when (of) {
            is OverlayScene.Companion -> "Companion ${of.level}"
            is OverlayScene.Resting -> "Resting ${of.pose}"
            else -> labelOf(of)
        }

    // Explicit names: class names are obfuscated in release builds, and this line measures RNF-R03.
    private fun labelOf(shown: OverlayScene): String =
        when (shown) {
            is OverlayScene.Companion -> "Companion"
            is OverlayScene.Resting -> "Resting"
            is OverlayScene.Fury -> "Fury"
            OverlayScene.Withdrawn, OverlayScene.Hidden, is OverlayScene.Ejected -> "None"
        }

    private data class DragAnchor(
        val windowCorner: IntOffset,
        val likkaOffset: IntOffset,
    )

    private data class Measure(
        val size: IntSize,
        val spritePixelPx: Int,
    )

    private companion object {
        const val FURY_LEVEL = 3
        const val SHAKE_LEVEL = 2
        const val NO_SHAKE = 0

        // A change already due is applied on the next turn, never in a busy loop.
        const val MIN_TICK_WAIT_MS = 1L
    }
}
