package com.likkapet.domain

import com.likkapet.domain.model.LikkaOverlayState
import com.likkapet.domain.model.MotionMode
import com.likkapet.domain.model.MotionPose
import com.likkapet.domain.model.OverlayEdge
import com.likkapet.domain.model.OverlayGeometry
import com.likkapet.domain.model.OverlayMotion
import com.likkapet.domain.model.OverlayVisibility
import com.likkapet.domain.port.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * Where the Level 1–2 overlay goes and which pose it shows (documentación §9.6, RF-O09–RF-O14).
 *
 * Same style as [EscalationCoordinator]: synchronous, no scheduled work. Every event first advances
 * the running timers to [Clock.nowMillis]; the service calls [onTick] at
 * [EscalationConfig.MOVEMENT_STEP_FPS] and applies [motion] to the window. A late tick catches up
 * the missed steps. Timers and position freeze while the overlay is hidden, during the farewell
 * and with system animations off; placing Likka (entering a level, a Level 1 drop, a new geometry)
 * is not movement and happens anyway.
 *
 * Level 1 hops to a random edge position every [EscalationConfig.LEVEL_1_HOP_INTERVAL_SEC]; a drag
 * ends on the nearest edge without touching the hop timer. Level 2 walks for
 * [EscalationConfig.LEVEL_2_WALK_SEC] towards the central zone and stops for
 * [EscalationConfig.LEVEL_2_STOP_SEC]; after a drag it waits [EscalationConfig.LEVEL_2_RETURN_DELAY_SEC]
 * and walks back to the central zone with no time limit, then stops.
 */
class OverlayMotionPlanner(
    private val clock: Clock,
    private val random: Random,
) {
    private var geometry: OverlayGeometry? = null
    private var level = 0
    private var isVisible = true
    private var isFarewell = false
    private var areAnimationsEnabled = true

    private var mode: MotionMode? = null
    private var position: ScreenPoint? = null
    private var edge: OverlayEdge? = null
    private var target: ScreenPoint? = null
    private var walkPose = MotionPose.WALK_RIGHT
    private var isReturning = false
    private var hopElapsedMs = 0L
    private var phaseElapsedMs = 0L
    private var stepElapsedMs = 0L
    private var lastUpdateMs = clock.nowMillis()

    private val mutableMotion = MutableStateFlow<OverlayMotion?>(null)

    /** null while there is nothing to place: level 0 or 3, or no geometry yet. */
    val motion: StateFlow<OverlayMotion?> = mutableMotion.asStateFlow()

    @Synchronized
    fun onTick() = update { }

    /** Only the level, the visibility and the farewell flag matter here. */
    @Synchronized
    fun onOverlayStateChanged(state: LikkaOverlayState) =
        update {
            isVisible = state.visibility == OverlayVisibility.SHOWN
            isFarewell = state.isFarewell
            val newLevel = state.level.takeIf { it in MOVING_LEVELS } ?: 0
            if (newLevel != level) enterLevel(newLevel)
        }

    @Synchronized
    fun onGeometryChanged(geometry: OverlayGeometry) =
        update {
            this.geometry = geometry
            refit()
        }

    /** false when `ANIMATOR_DURATION_SCALE == 0` (*Quitar animaciones*, RF-O14). */
    @Synchronized
    fun onAnimationsEnabledChanged(areEnabled: Boolean) = update { areAnimationsEnabled = areEnabled }

    @Synchronized
    fun onDragStarted() = update { if (position != null) mode = MotionMode.DRAGGED }

    /** [windowX]/[windowY]: the window's top-left corner where the finger let go. */
    @Synchronized
    fun onDragEnded(
        windowX: Int,
        windowY: Int,
    ) = update { if (mode == MotionMode.DRAGGED) drop(ScreenPoint(windowX, windowY)) }

    private val safeArea: SafeArea? get() = geometry?.let(::SafeArea)

    private val isMoving: Boolean
        get() = mode != null && isVisible && !isFarewell && areAnimationsEnabled

    private inline fun update(event: () -> Unit) {
        advanceTo(clock.nowMillis())
        event()
        placeIfNeeded()
        publish()
    }

    private fun advanceTo(nowMs: Long) {
        val deltaMs = nowMs - lastUpdateMs
        if (deltaMs <= 0L) return
        lastUpdateMs = nowMs
        if (isMoving) advanceBy(deltaMs)
    }

    // Splits the time at every event (hop, step, phase end) so a late tick replays them in order.
    private fun advanceBy(deltaMs: Long) {
        if (mode == MotionMode.DRAGGED) return advanceWhileDragged(deltaMs)
        var remainingMs = deltaMs
        while (remainingMs > 0L) {
            val untilEventMs = msUntilNextEvent() ?: return
            val sliceMs = minOf(remainingMs, untilEventMs)
            addElapsed(sliceMs)
            remainingMs -= sliceMs
            if (sliceMs == untilEventMs) fireEvent()
        }
    }

    // Level 1 keeps its hop timer running under the finger; a hop due meanwhile waits for the drop.
    private fun advanceWhileDragged(deltaMs: Long) {
        if (level == 1) hopElapsedMs = minOf(EscalationTimings.LEVEL_1_HOP_INTERVAL_MS, hopElapsedMs + deltaMs)
    }

    private fun msUntilNextEvent(): Long? =
        when (mode) {
            MotionMode.ON_EDGE -> EscalationTimings.LEVEL_1_HOP_INTERVAL_MS - hopElapsedMs
            MotionMode.WALKING -> msUntilWalkEvent()
            MotionMode.STOPPED -> EscalationTimings.LEVEL_2_STOP_MS - phaseElapsedMs
            MotionMode.WAITING_TO_RETURN -> EscalationTimings.LEVEL_2_RETURN_DELAY_MS - phaseElapsedMs
            MotionMode.DRAGGED, null -> null
        }

    private fun msUntilWalkEvent(): Long {
        val untilStepMs = EscalationTimings.MOVEMENT_STEP_MS - stepElapsedMs
        if (isReturning) return untilStepMs
        return minOf(untilStepMs, EscalationTimings.LEVEL_2_WALK_MS - phaseElapsedMs)
    }

    private fun addElapsed(sliceMs: Long) {
        when (mode) {
            MotionMode.ON_EDGE -> {
                hopElapsedMs += sliceMs
            }

            MotionMode.WALKING -> {
                phaseElapsedMs += sliceMs
                stepElapsedMs += sliceMs
            }

            else -> {
                phaseElapsedMs += sliceMs
            }
        }
    }

    private fun fireEvent() {
        when (mode) {
            MotionMode.ON_EDGE -> hop()
            MotionMode.WALKING -> onWalkEvent()
            MotionMode.STOPPED -> startWalking(isReturn = false)
            MotionMode.WAITING_TO_RETURN -> startWalking(isReturn = true)
            MotionMode.DRAGGED, null -> Unit
        }
    }

    // Level 1 hops away from wherever Level 2 left it; Level 2 starts walking from the Level 1 spot.
    // Under the finger the window stays the user's: the drop then lands by the new level's rules.
    private fun enterLevel(newLevel: Int) {
        level = newLevel
        target = null
        hopElapsedMs = 0L
        when {
            newLevel !in MOVING_LEVELS -> clearMotion()
            mode == MotionMode.DRAGGED -> Unit
            newLevel == 1 -> position?.let { hop() }
            else -> position?.let { startWalking(isReturn = false) }
        }
    }

    // Entering a level before the first geometry arrives: place Likka as soon as it does.
    private fun placeIfNeeded() {
        val area = safeArea ?: return
        if (level !in MOVING_LEVELS || position != null) return
        val start = EdgePlacement.random(area, random, avoid = null)
        position = start.point
        edge = start.edge
        if (level == 1) enterEdgeMode() else startWalking(isReturn = false)
    }

    private fun hop() {
        val area = safeArea ?: return
        val next = EdgePlacement.random(area, random, avoid = currentEdgePosition())
        position = next.point
        edge = next.edge
        enterEdgeMode()
    }

    private fun enterEdgeMode() {
        mode = MotionMode.ON_EDGE
        hopElapsedMs = 0L
    }

    private fun startWalking(isReturn: Boolean) {
        mode = MotionMode.WALKING
        edge = null
        isReturning = isReturn
        phaseElapsedMs = 0L
        stepElapsedMs = 0L
        if (isReturn || target == null || target == position) chooseTarget()
    }

    private fun onWalkEvent() {
        if (stepElapsedMs >= EscalationTimings.MOVEMENT_STEP_MS) takeStep()
        if (mode == MotionMode.WALKING && !isReturning && phaseElapsedMs >= EscalationTimings.LEVEL_2_WALK_MS) stop()
    }

    private fun takeStep() {
        // Reset first: advanceBy only terminates if every fired event restarts its own timer.
        stepElapsedMs = 0L
        val from = position ?: return
        val to = target ?: return
        val area = safeArea ?: return
        val stepPx = EscalationConfig.LEVEL_2_WALK_STEP_SPRITE_PX * area.geometry.spritePixelPx
        position = WalkPlanning.stepTowards(from, to, stepPx)
        if (position != to) return
        // Arrived: the way back ends in a stop; the regular cycle keeps wandering until its time is up.
        if (isReturning) stop() else chooseTarget()
    }

    private fun stop() {
        mode = MotionMode.STOPPED
        isReturning = false
        phaseElapsedMs = 0L
    }

    private fun chooseTarget() {
        val area = safeArea ?: return
        val from = position ?: return
        val next = WalkPlanning.centralZoneTarget(area, random)
        target = next
        walkPose = WalkPlanning.walkPoseFor(next.x - from.x, next.y - from.y) ?: walkPose
    }

    private fun drop(point: ScreenPoint) {
        val area = safeArea ?: return
        if (level == 1) {
            val snapped = EdgePlacement.nearest(area, point)
            position = snapped.point
            edge = snapped.edge
            mode = MotionMode.ON_EDGE
        } else {
            position = ScreenPoint(area.snapX(point.x), area.snapY(point.y))
            mode = MotionMode.WAITING_TO_RETURN
            phaseElapsedMs = 0L
        }
    }

    // A new geometry (rotation, level size, roast length) must not leave the window in the insets.
    // Level 2 also drops its target, chosen for the old window: a walk re-aims (and re-picks its
    // pose) right away, any other mode picks a new one when it starts walking again.
    private fun refit() {
        val area = safeArea ?: return
        val current = position ?: return
        val currentEdge = edge
        if (mode == MotionMode.ON_EDGE && currentEdge != null) {
            position = EdgePlacement.at(area, currentEdge, current).point
            return
        }
        position = area.clamp(current)
        target = null
        if (mode == MotionMode.WALKING) chooseTarget()
    }

    private fun currentEdgePosition(): EdgePosition? {
        val currentEdge = edge ?: return null
        val current = position ?: return null
        return EdgePosition(currentEdge, current)
    }

    private fun clearMotion() {
        mode = null
        position = null
        edge = null
        target = null
        isReturning = false
    }

    private fun publish() {
        val currentMode = mode
        val current = position
        mutableMotion.value =
            if (currentMode == null || current == null || geometry == null) {
                null
            } else {
                OverlayMotion(
                    x = current.x,
                    y = current.y,
                    pose = poseFor(currentMode),
                    mode = currentMode,
                    edge = edge.takeIf { currentMode == MotionMode.ON_EDGE },
                )
            }
    }

    // Level 1 perches only on the bottom edge; Level 2 never reaches ON_EDGE, so it never perches.
    private fun poseFor(mode: MotionMode): MotionPose =
        when (mode) {
            MotionMode.ON_EDGE -> if (edge == OverlayEdge.BOTTOM) MotionPose.PERCH else MotionPose.PEEK
            MotionMode.WALKING -> walkPose
            MotionMode.STOPPED, MotionMode.WAITING_TO_RETURN -> MotionPose.ANNOYED
            MotionMode.DRAGGED -> MotionPose.DRAGGED
        }

    private companion object {
        val MOVING_LEVELS = 1..2
    }
}
