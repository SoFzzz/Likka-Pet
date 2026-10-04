package com.likkapet.service

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.hypot
import kotlin.math.roundToInt

/** What a drag on the overlay window reports, in screen pixels from where the finger went down. */
interface OverlayDragListener {
    /** A finger went down on the window: a drag may follow, measured from where the window is now. */
    fun onTouchDown()

    fun onDragStarted()

    fun onDragMoved(
        dxPx: Int,
        dyPx: Int,
    )

    fun onDragEnded(
        dxPx: Int,
        dyPx: Int,
    )
}

/**
 * The overlay window's root (RF-O11, RF-O12): taps and long presses go to the Compose content as
 * usual; once the finger moves past the touch slop the gesture becomes a drag of the whole window,
 * and the content gets a cancel instead of a click. Distances come from the raw screen coordinates
 * of the first finger, because the window itself moves under it; a second finger is ignored and
 * lifting the first one ends the drag. Only while [isDragEnabled] (Levels 1–2), so the Level 3
 * panel keeps its scrolling. Main thread only.
 */
@SuppressLint("ViewConstructor")
class OverlayDragLayout(
    context: Context,
    private val listener: OverlayDragListener,
) : FrameLayout(context) {
    private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var isDragging = false
    private var lastDxPx = 0
    private var lastDyPx = 0

    var isDragEnabled = false
        set(value) {
            field = value
            if (!value) forgetGesture()
        }

    private val isTracking: Boolean get() = pointerId != MotionEvent.INVALID_POINTER_ID

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = track(event)

    // Reached when no child takes the gesture (outside Likka's touch target), or once intercepted.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = track(event) || isTracking

    private fun track(event: MotionEvent): Boolean {
        if (!isDragEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event)
            MotionEvent.ACTION_MOVE -> onMove(event)
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointerId) onRelease(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onRelease(event)
        }
        return isDragging
    }

    private fun onDown(event: MotionEvent) {
        pointerId = event.getPointerId(0)
        downRawX = event.rawX
        downRawY = event.rawY
        isDragging = false
        lastDxPx = 0
        lastDyPx = 0
        listener.onTouchDown()
    }

    private fun onMove(event: MotionEvent) {
        val index = trackedIndex(event) ?: return
        val dx = event.getRawX(index) - downRawX
        val dy = event.getRawY(index) - downRawY
        if (!isDragging && hypot(dx, dy) > touchSlopPx) {
            isDragging = true
            listener.onDragStarted()
        }
        if (!isDragging) return
        lastDxPx = dx.roundToInt()
        lastDyPx = dy.roundToInt()
        listener.onDragMoved(lastDxPx, lastDyPx)
    }

    // Ends where the finger lifted, or where it last was if this event no longer carries it.
    private fun onRelease(event: MotionEvent) {
        if (isDragging) {
            val index = trackedIndex(event)
            val dx = index?.let { (event.getRawX(it) - downRawX).roundToInt() } ?: lastDxPx
            val dy = index?.let { (event.getRawY(it) - downRawY).roundToInt() } ?: lastDyPx
            listener.onDragEnded(dx, dy)
        }
        forgetGesture()
    }

    private fun trackedIndex(event: MotionEvent): Int? {
        if (!isTracking) return null
        return event.findPointerIndex(pointerId).takeIf { it >= 0 }
    }

    private fun forgetGesture() {
        pointerId = MotionEvent.INVALID_POINTER_ID
        isDragging = false
    }
}
