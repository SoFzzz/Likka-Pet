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
import kotlin.math.abs
import kotlin.random.Random

/**
 * Where the Level 1–2 overlay goes and which pose it shows (documentación §9.6, RF-O09–RF-O14).
 *
 * Same style as [EscalationCoordinator]: synchronous, no scheduled work. Every event first advances
 * the running timers to [Clock.nowMillis]; the service calls [onTick] when [msUntilNextChange] says
 * (each step of [EscalationConfig.MOVEMENT_STEP_FPS] while walking, the next hop or phase end
 * otherwise) and applies [motion] to the window; while dragged, [onDragMoved] keeps the window under
 * the finger. A late tick catches up
 * the missed steps. Timers and position freeze while the overlay is hidden, during the farewell
 * and with system animations off; placing Likka (entering a level, a Level 1 drop, a new geometry)
 * is not movement and happens anyway.
 *
 * Level 1 hops to a random edge position every [EscalationConfig.LEVEL_1_HOP_INTERVAL_SEC]; a drag
 * ends on the nearest edge without touching the hop timer. Level 2 walks in straight segments in
 * one of the 4 cardinal directions (at least [EscalationConfig.LEVEL_2_WALK_MIN_SEC] when space
 * allows) across the safe area and stops for [EscalationConfig.LEVEL_2_STOP_SEC]; after a drag it
 * waits [EscalationConfig.LEVEL_2_RETURN_DELAY_SEC] and walks back to the central zone in straight
 * segments without a time limit, then stops.
 *
 * At level 0 the farewell (RF-O04) and the pause still show Likka, so they get a RESTING motion:
 * the last Level 1–2 spot is kept (Likka says goodbye or sits where it was), or, with no spot yet
 * (after the Level 3 panel, or a pause started before any level), a random edge position.
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
    private var stoppedPose: MotionPose? = null
    private var restingPose: MotionPose? = null
    private var lastWalkDirection: WalkDirection? = null
    private var returnFinalTarget: ScreenPoint? = null
    private var isReturning = false
    private var hopElapsedMs = 0L
    private var phaseElapsedMs = 0L
    private var stepElapsedMs = 0L
    private var lastUpdateMs = clock.nowMillis()

    private val mutableMotion = MutableStateFlow<OverlayMotion?>(null)

    /** null while there is nothing to place: level 3, level 0 with no farewell nor pause, or no geometry yet. */
    val motion: StateFlow<OverlayMotion?> = mutableMotion.asStateFlow()

    @Synchronized
    fun onTick() = update { }

    /** Only the level, the visibility and the farewell and pause flags matter here. */
    @Synchronized
    fun onOverlayStateChanged(state: LikkaOverlayState) =
        update {
            isVisible = state.visibility == OverlayVisibility.SHOWN
            isFarewell = state.isFarewell
            val newLevel = state.level.takeIf { it in MOVING_LEVELS } ?: 0
            if (newLevel != level) enterLevel(newLevel)
            if (level == 0) rest(restingPoseFor(state))
        }

    @Synchronized
    fun onGeometryChanged(geometry: OverlayGeometry) =
        update {
            // The same geometry again is not a change: re-fitting would abandon the walk under way.
            if (geometry == this.geometry) return@update
            this.geometry = geometry
            refit()
        }

    /** false when `ANIMATOR_DURATION_SCALE == 0` (*Quitar animaciones*, RF-O14). */
    @Synchronized
    fun onAnimationsEnabledChanged(areEnabled: Boolean) = update { areAnimationsEnabled = areEnabled }

    @Synchronized
    fun onDragStarted() = update { if (position != null && level in MOVING_LEVELS) mode = MotionMode.DRAGGED }

    /**
     * The finger moved: the window's top-left corner follows it to ([windowX], [windowY]), kept in
     * the safe area (RF-O13) and on the sprite-pixel grid. Only while dragged.
     */
    @Synchronized
    fun onDragMoved(
        windowX: Int,
        windowY: Int,
    ) = update {
        val area = safeArea
        if (mode == MotionMode.DRAGGED && area != null) position = ScreenPoint(area.snapX(windowX), area.snapY(windowY))
    }

    /** [windowX]/[windowY]: the window's top-left corner where the finger let go. */
    @Synchronized
    fun onDragEnded(
        windowX: Int,
        windowY: Int,
    ) = update { if (mode == MotionMode.DRAGGED) drop(ScreenPoint(windowX, windowY)) }

    /**
     * How long until the movement changes on its own (a walking step, a hop, the end of a phase),
     * so the service can sleep until then instead of ticking for nothing; null when nothing will
     * change without an event (frozen, hidden, dragged, resting or nothing placed).
     */
    @Synchronized
    fun msUntilNextChange(): Long? {
        if (!isMoving) return null
        val untilEventMs = msUntilNextEvent() ?: return null
        return (untilEventMs - (clock.nowMillis() - lastUpdateMs)).coerceAtLeast(0L)
    }

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
            MotionMode.WALKING -> EscalationTimings.MOVEMENT_STEP_MS - stepElapsedMs
            MotionMode.STOPPED -> EscalationTimings.LEVEL_2_STOP_MS - phaseElapsedMs
            MotionMode.WAITING_TO_RETURN -> EscalationTimings.LEVEL_2_RETURN_DELAY_MS - phaseElapsedMs
            MotionMode.DRAGGED, MotionMode.RESTING, null -> null
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
            MotionMode.DRAGGED, MotionMode.RESTING, null -> Unit
        }
    }

    // Level 1 hops away from wherever Level 2 (or a rest) left it; Level 2 starts walking from there.
    // Under the finger the window stays the user's: the drop then lands by the new level's rules.
    // Leaving the moving levels keeps the spot for a farewell or a pause; rest() decides.
    private fun enterLevel(newLevel: Int) {
        level = newLevel
        target = null
        hopElapsedMs = 0L
        lastWalkDirection = null
        returnFinalTarget = null
        when {
            newLevel !in MOVING_LEVELS -> Unit
            mode == MotionMode.DRAGGED -> Unit
            newLevel == 1 -> position?.let { hop() }
            else -> position?.let { startWalking(isReturn = false) }
        }
        if (newLevel in MOVING_LEVELS) restingPose = null
    }

    private fun restingPoseFor(state: LikkaOverlayState): MotionPose? =
        when {
            state.level != 0 -> null
            state.isFarewell -> MotionPose.GOODBYE
            state.isPaused -> MotionPose.SIT
            else -> null
        }

    // Level 0 (or 3, which the planner does not place): nothing to rest means nothing to place.
    private fun rest(pose: MotionPose?) {
        if (pose == null) return clearMotion()
        restingPose = pose
        if (position != null) mode = MotionMode.RESTING
    }

    // Entering a level or a rest before the first geometry arrives (or after the Level 3 panel,
    // which leaves no spot): place Likka as soon as possible.
    private fun placeIfNeeded() {
        val area = safeArea ?: return
        if (position != null) return
        if (level !in MOVING_LEVELS && restingPose == null) return
        val start = EdgePlacement.random(area, random, avoid = null)
        position = start.point
        edge = start.edge
        when {
            level == 1 -> enterEdgeMode()
            level == 2 -> startWalking(isReturn = false)
            else -> mode = MotionMode.RESTING
        }
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
        if (isReturn) {
            startReturnWalk()
        } else {
            startNormalTramo()
        }
    }

    private fun startNormalTramo() {
        val area = safeArea ?: return
        val from = position ?: return
        val stepPx = EscalationConfig.LEVEL_2_WALK_STEP_SPRITE_PX * area.geometry.spritePixelPx
        val minSteps = EscalationConfig.LEVEL_2_WALK_MIN_SEC * EscalationConfig.MOVEMENT_STEP_FPS
        val tramo =
            WalkPlanning.planNextTramo(
                area = area,
                from = from,
                stepPx = stepPx,
                minSteps = minSteps,
                previousDirection = lastWalkDirection,
                random = random,
            )
        if (tramo == null) {
            stop(keepPose = true)
            return
        }
        lastWalkDirection = tramo.direction
        target = tramo.target
        walkPose = tramo.direction.pose
        stoppedPose = null
    }

    private fun startReturnWalk() {
        val area = safeArea ?: return
        val from = position ?: return
        val finalDest = returnFinalTarget ?: WalkPlanning.centralZoneTarget(area, random).also { returnFinalTarget = it }
        val dx = finalDest.x - from.x
        val dy = finalDest.y - from.y
        if (dx == 0 && dy == 0) {
            returnFinalTarget = null
            stop(keepPose = false)
            return
        }
        val chooseXFirst = abs(dx) >= abs(dy)
        val nextTarget =
            if (chooseXFirst && dx != 0) {
                ScreenPoint(finalDest.x, from.y)
            } else if (!chooseXFirst && dy != 0) {
                ScreenPoint(from.x, finalDest.y)
            } else if (dx != 0) {
                ScreenPoint(finalDest.x, from.y)
            } else {
                ScreenPoint(from.x, finalDest.y)
            }
        val dir =
            if (nextTarget.x != from.x) {
                if (nextTarget.x > from.x) WalkDirection.RIGHT else WalkDirection.LEFT
            } else {
                if (nextTarget.y > from.y) WalkDirection.DOWN else WalkDirection.UP
            }
        lastWalkDirection = dir
        target = nextTarget
        walkPose = dir.pose
        stoppedPose = null
    }

    private fun onWalkEvent() {
        if (stepElapsedMs >= EscalationTimings.MOVEMENT_STEP_MS) takeStep()
    }

    private fun takeStep() {
        stepElapsedMs = 0L
        val from = position ?: return
        val to = target ?: return
        val area = safeArea ?: return
        val stepPx = EscalationConfig.LEVEL_2_WALK_STEP_SPRITE_PX * area.geometry.spritePixelPx
        val next = WalkPlanning.stepTowards(from, to, stepPx)
        if (next == from) {
            // Cannot advance further (hit border of safe area)
            if (isReturning) returnFinalTarget = null
            stop(keepPose = false)
            return
        }
        position = next
        if (position != to) return

        // Arrived at target:
        if (isReturning) {
            val finalDest = returnFinalTarget
            if (finalDest == null || position == finalDest) {
                returnFinalTarget = null
                stop(keepPose = false)
            } else {
                startReturnWalk()
            }
        } else {
            stop(keepPose = false)
        }
    }

    private fun stop(keepPose: Boolean) {
        mode = MotionMode.STOPPED
        isReturning = false
        phaseElapsedMs = 0L
        target = null
        if (!keepPose) {
            stoppedPose = MotionPose.ANNOYED
        }
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
            stoppedPose = MotionPose.ANNOYED
            phaseElapsedMs = 0L
            returnFinalTarget = null
            target = null
        }
    }

    // A new geometry (rotation, level size, roast length) must not leave the window in the insets.
    // Level 2 re-aims in the new safe area; any other mode picks a new one when it starts walking again.
    private fun refit() {
        val area = safeArea ?: return
        val current = position ?: return
        val currentEdge = edge
        val isOnEdge = mode == MotionMode.ON_EDGE || mode == MotionMode.RESTING
        if (isOnEdge && currentEdge != null) {
            position = EdgePlacement.at(area, currentEdge, current).point
            return
        }
        position = area.clamp(current)
        target = null
        if (mode == MotionMode.WALKING) {
            if (isReturning) startReturnWalk() else startNormalTramo()
        }
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
        restingPose = null
        isReturning = false
        lastWalkDirection = null
        returnFinalTarget = null
        stoppedPose = null
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
            MotionMode.STOPPED, MotionMode.WAITING_TO_RETURN -> stoppedPose ?: MotionPose.ANNOYED
            MotionMode.DRAGGED -> MotionPose.DRAGGED
            MotionMode.RESTING -> restingPose ?: MotionPose.SIT
        }

    private companion object {
        val MOVING_LEVELS = 1..2
    }
}
