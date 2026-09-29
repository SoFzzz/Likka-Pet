package com.likkapet.domain

import com.likkapet.domain.model.LikkaOverlayState
import com.likkapet.domain.model.OverlayGeometry
import com.likkapet.domain.model.OverlayInsets
import com.likkapet.domain.model.OverlayMotion
import com.likkapet.domain.model.OverlayVisibility
import com.likkapet.domain.model.TriggerReason
import org.junit.Assert.assertTrue
import kotlin.random.Random

/**
 * Drives an [OverlayMotionPlanner] the way the service will: one [OverlayMotionPlanner.onTick] per
 * movement step of virtual time, with a seeded [Random] so every run is reproducible.
 */
class PlannerHarness(
    seed: Int = DEFAULT_SEED,
    initialGeometry: OverlayGeometry = PHONE,
) {
    val clock = FakeClock()
    val planner = OverlayMotionPlanner(clock, Random(seed))

    var geometry = initialGeometry
        private set

    private var level = 0

    init {
        planner.onGeometryChanged(initialGeometry)
    }

    val motion: OverlayMotion get() = checkNotNull(planner.motion.value) { "No motion published" }

    internal val area: SafeArea get() = SafeArea(geometry)

    fun showLevel(level: Int) = setOverlay(level, OverlayVisibility.SHOWN)

    fun hide() = setOverlay(level, OverlayVisibility.HIDDEN_WHILE_AWAY)

    fun show() = setOverlay(level, OverlayVisibility.SHOWN)

    fun startFarewell() = planner.onOverlayStateChanged(overlayState(level, OverlayVisibility.SHOWN).copy(isFarewell = true))

    fun changeGeometry(newGeometry: OverlayGeometry) {
        geometry = newGeometry
        planner.onGeometryChanged(newGeometry)
    }

    fun dragTo(
        x: Int,
        y: Int,
    ) {
        planner.onDragStarted()
        planner.onDragEnded(x, y)
    }

    /** Ticks every movement step and returns what was published after each one. */
    fun advanceBy(
        seconds: Int = 0,
        millis: Long = 0L,
    ): List<OverlayMotion?> {
        val steps = (seconds * EscalationTimings.MILLIS_PER_SECOND + millis) / STEP_MS
        return (1..steps).map {
            clock.nowMs += STEP_MS
            planner.onTick()
            planner.motion.value
        }
    }

    /** One single tick after a long gap, e.g. the service thread delayed by Doze. */
    fun jumpBy(seconds: Int) {
        clock.nowMs += seconds * EscalationTimings.MILLIS_PER_SECOND
        planner.onTick()
    }

    fun assertInsideSafeArea(motion: OverlayMotion?) {
        val shown = checkNotNull(motion)
        val insets = geometry.insets
        val isInside =
            shown.x >= insets.left &&
                shown.y >= insets.top &&
                shown.x + geometry.windowWidthPx <= geometry.screenWidthPx - insets.right &&
                shown.y + geometry.windowHeightPx <= geometry.screenHeightPx - insets.bottom
        assertTrue("$shown overlaps the insets of $geometry", isInside)
    }

    private fun setOverlay(
        level: Int,
        visibility: OverlayVisibility,
    ) {
        this.level = level
        planner.onOverlayStateChanged(overlayState(level, visibility))
    }

    private fun overlayState(
        level: Int,
        visibility: OverlayVisibility,
    ) = LikkaOverlayState.NOTHING_TO_SHOW.copy(
        visibility = visibility,
        level = level,
        reason = TriggerReason.POSTURE.takeIf { level > 0 },
    )

    companion object {
        const val DEFAULT_SEED = 7
        val STEP_MS = EscalationTimings.MOVEMENT_STEP_MS

        // Redmi 9-like portrait screen: status bar on top, 3-button navigation bar at the bottom.
        val PHONE =
            OverlayGeometry(
                screenWidthPx = 720,
                screenHeightPx = 1600,
                insets = OverlayInsets(left = 0, top = 72, right = 0, bottom = 96),
                windowWidthPx = 198,
                windowHeightPx = 240,
                spritePixelPx = 3,
            )

        val TEST_SCREENS =
            listOf(
                PHONE,
                OverlayGeometry(480, 800, OverlayInsets(0, 48, 0, 72), 150, 180, 2),
                OverlayGeometry(1600, 2560, OverlayInsets(0, 100, 0, 140), 330, 400, 5),
                // Landscape: camera cutout on the left, navigation bar on the right
                OverlayGeometry(1600, 720, OverlayInsets(80, 72, 96, 0), 198, 240, 3),
                // Narrow: the window is almost as wide as the safe area
                OverlayGeometry(400, 900, OverlayInsets(0, 60, 0, 80), 380, 300, 4),
            )
    }
}
