package com.likkapet.domain

import com.likkapet.domain.model.LikkaOverlayState
import com.likkapet.domain.model.LikkaState
import com.likkapet.domain.model.PostureReading
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.port.Clock

/** Virtual time for domain tests: only [advanceBy] moves it, never the real clock. */
class FakeClock(
    var nowMs: Long = 0L,
) : Clock {
    override fun nowMillis(): Long = nowMs
}

/**
 * Drives an [EscalationCoordinator] the way LikkaService will: events plus one [EscalationCoordinator.onTick]
 * per virtual second, so every threshold in [EscalationConfig] (whole seconds) is hit exactly.
 */
class CoordinatorHarness {
    val clock = FakeClock()
    val coordinator = EscalationCoordinator(clock)

    val overlay: LikkaOverlayState get() = coordinator.overlayState.value
    val state: LikkaState get() = coordinator.likkaState.value

    fun advanceBy(
        minutes: Int = 0,
        seconds: Int = 0,
    ) {
        repeat(minutes * SECONDS_PER_MINUTE + seconds) {
            clock.nowMs += MILLIS_PER_SECOND
            coordinator.onTick()
        }
    }

    /** One single tick after a long gap, e.g. the service thread delayed by Doze. */
    fun jumpBy(seconds: Int) {
        clock.nowMs += seconds * MILLIS_PER_SECOND
        coordinator.onTick()
    }

    fun openApp(app: TargetApp = TargetApp.TIKTOK) = coordinator.onForegroundAppChanged(app)

    fun leaveWatchedApps() = coordinator.onForegroundAppChanged(null)

    /** "Me rindo" followed by what the service reports once HomeLauncher opens the launcher. */
    fun surrenderAndGoHome() {
        coordinator.onSurrenderClick()
        leaveWatchedApps()
    }

    fun holdPosture(angleDegrees: Double) = coordinator.onPostureReading(PostureReading(angleDegrees, isOnTable = false))

    fun putOnTable() = coordinator.onPostureReading(PostureReading(TABLE_ANGLE, isOnTable = true))

    /** Watched app open and posture fine: only USAGE_TIME can escalate. */
    fun startSessionWithGoodPosture() {
        openApp()
        holdPosture(GOOD_ANGLE)
    }

    fun reachPostureLevel1() {
        openApp()
        holdPosture(BAD_ANGLE)
        advanceBy(seconds = EscalationConfig.POSTURE_TRIGGER_SEC)
    }

    fun forgivePosture() {
        holdPosture(GOOD_ANGLE)
        advanceBy(seconds = EscalationConfig.POSTURE_RESET_SEC)
    }

    companion object {
        const val BAD_ANGLE = 30.0
        const val GOOD_ANGLE = 70.0
        const val NEUTRAL_ANGLE = 50.0
        const val TABLE_ANGLE = 2.0
        private const val SECONDS_PER_MINUTE = 60
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
