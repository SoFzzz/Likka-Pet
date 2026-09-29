package com.likkapet.domain

import com.likkapet.domain.CoordinatorHarness.Companion.BAD_ANGLE
import com.likkapet.domain.CoordinatorHarness.Companion.GOOD_ANGLE
import com.likkapet.domain.CoordinatorHarness.Companion.NEUTRAL_ANGLE
import com.likkapet.domain.model.ReasonLevel
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.TriggerReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-track transitions of documentación §3.3 and the §12.1 `EscalationCoordinator` cases. */
class ReasonTracksTest {
    private val harness = CoordinatorHarness()
    private val coordinator = harness.coordinator

    @Test
    fun `posture triggers after 10 seconds of bad posture but not after 9`() {
        harness.openApp()
        harness.holdPosture(BAD_ANGLE)

        harness.advanceBy(seconds = 9)
        assertEquals(0, harness.overlay.level)

        harness.advanceBy(seconds = 1)
        assertEquals(1, harness.overlay.level)
        assertEquals(TriggerReason.POSTURE, harness.overlay.reason)
    }

    @Test
    fun `posture is forgiven after 15 seconds of good posture but not after 14`() {
        harness.reachPostureLevel1()
        harness.holdPosture(GOOD_ANGLE)

        harness.advanceBy(seconds = 14)
        assertEquals(1, harness.overlay.level)

        harness.advanceBy(seconds = 1)
        assertEquals(0, harness.overlay.level)
    }

    @Test
    fun `angle between the hysteresis thresholds neither triggers nor forgives`() {
        harness.openApp()
        harness.holdPosture(NEUTRAL_ANGLE)
        harness.advanceBy(minutes = 1)
        assertEquals(0, harness.overlay.level)

        harness.holdPosture(BAD_ANGLE)
        harness.advanceBy(seconds = 10)
        harness.holdPosture(NEUTRAL_ANGLE)
        harness.advanceBy(seconds = 30)
        assertEquals(1, harness.overlay.level)
    }

    @Test
    fun `a bad posture streak broken by a better angle starts counting again`() {
        harness.openApp()
        harness.holdPosture(BAD_ANGLE)
        harness.advanceBy(seconds = 8)
        harness.holdPosture(NEUTRAL_ANGLE)
        harness.advanceBy(seconds = 1)
        harness.holdPosture(BAD_ANGLE)

        harness.advanceBy(seconds = 9)
        assertEquals(0, harness.overlay.level)
        harness.advanceBy(seconds = 1)
        assertEquals(1, harness.overlay.level)
    }

    @Test
    fun `posture track escalates from level 1 to 2 after 2 minutes and to 3 after 3 more`() {
        harness.reachPostureLevel1()

        harness.advanceBy(minutes = 1, seconds = 59)
        assertEquals(1, harness.overlay.level)
        harness.advanceBy(seconds = 1)
        assertEquals(2, harness.overlay.level)

        harness.advanceBy(minutes = 2, seconds = 59)
        assertEquals(2, harness.overlay.level)
        harness.advanceBy(seconds = 1)
        assertEquals(3, harness.overlay.level)
        assertEquals(EscalationConfig.LEVEL_3_AUTO_HOME_SEC, harness.overlay.level3SecondsLeft)
    }

    @Test
    fun `posture keeps escalating while the angle sits between the thresholds`() {
        harness.reachPostureLevel1()
        harness.holdPosture(NEUTRAL_ANGLE)

        harness.advanceBy(minutes = 2)

        assertEquals(2, harness.overlay.level)
    }

    @Test
    fun `usage time triggers level 1 after 15 minutes of session but not at 14 59`() {
        harness.startSessionWithGoodPosture()

        harness.advanceBy(minutes = 14, seconds = 59)
        assertEquals(0, harness.overlay.level)

        harness.advanceBy(seconds = 1)
        assertEquals(1, harness.overlay.level)
        assertEquals(TriggerReason.USAGE_TIME, harness.overlay.reason)
    }

    @Test
    fun `usage time track escalates from level 1 to 2 after 2 minutes and to 3 after 3 more`() {
        harness.startSessionWithGoodPosture()
        harness.advanceBy(minutes = 15)

        harness.advanceBy(minutes = 1, seconds = 59)
        assertEquals(1, harness.overlay.level)
        harness.advanceBy(seconds = 1)
        assertEquals(2, harness.overlay.level)

        harness.advanceBy(minutes = 2, seconds = 59)
        assertEquals(2, harness.overlay.level)
        harness.advanceBy(seconds = 1)
        assertEquals(3, harness.overlay.level)
    }

    @Test
    fun `switching between watched apps does not restart the leisure session`() {
        harness.startSessionWithGoodPosture()
        harness.advanceBy(minutes = 10)

        harness.openApp(TargetApp.INSTAGRAM)
        harness.advanceBy(minutes = 5)

        assertEquals(1, harness.overlay.level)
        assertEquals(TargetApp.INSTAGRAM, harness.overlay.app)
    }

    @Test
    fun `four minutes away pause the session counter without restarting it`() {
        harness.startSessionWithGoodPosture()
        harness.advanceBy(minutes = 10)
        harness.leaveWatchedApps()
        harness.advanceBy(minutes = 4)

        harness.openApp()
        harness.advanceBy(minutes = 4, seconds = 59)
        assertEquals(0, harness.overlay.level)
        harness.advanceBy(seconds = 1)
        assertEquals(1, harness.overlay.level)
    }

    @Test
    fun `five minutes away end the session and reset both tracks`() {
        harness.startSessionWithGoodPosture()
        harness.advanceBy(minutes = 14, seconds = 50)
        harness.holdPosture(BAD_ANGLE)
        harness.advanceBy(seconds = 10)
        assertEquals(ReasonLevel.LEVEL_1, coordinator.trackLevel(TriggerReason.POSTURE))
        assertEquals(ReasonLevel.LEVEL_1, coordinator.trackLevel(TriggerReason.USAGE_TIME))

        harness.leaveWatchedApps()
        harness.advanceBy(minutes = 5)
        harness.startSessionWithGoodPosture()

        assertEquals(ReasonLevel.NONE, coordinator.trackLevel(TriggerReason.POSTURE))
        assertEquals(ReasonLevel.NONE, coordinator.trackLevel(TriggerReason.USAGE_TIME))
        harness.advanceBy(minutes = 14, seconds = 59)
        assertEquals(0, harness.overlay.level)
    }

    @Test
    fun `both tracks escalate independently and the overlay shows the higher level`() {
        harness.startSessionWithGoodPosture()
        harness.advanceBy(minutes = 13)
        harness.holdPosture(BAD_ANGLE)
        harness.advanceBy(minutes = 2, seconds = 10)

        assertEquals(ReasonLevel.LEVEL_2, coordinator.trackLevel(TriggerReason.POSTURE))
        assertEquals(ReasonLevel.LEVEL_1, coordinator.trackLevel(TriggerReason.USAGE_TIME))
        assertEquals(2, harness.overlay.level)
        assertEquals(TriggerReason.POSTURE, harness.overlay.reason)
    }

    @Test
    fun `on a level tie the reason that has been active longer drives the overlay`() {
        harness.startSessionWithGoodPosture()
        harness.advanceBy(minutes = 15)
        harness.holdPosture(BAD_ANGLE)
        harness.advanceBy(seconds = 10)

        assertEquals(ReasonLevel.LEVEL_1, coordinator.trackLevel(TriggerReason.POSTURE))
        assertEquals(1, harness.overlay.level)
        assertEquals(TriggerReason.USAGE_TIME, harness.overlay.reason)
    }

    @Test
    fun `angles exactly at 45 and 55 degrees are neutral`() {
        harness.openApp()
        harness.holdPosture(EscalationConfig.POSTURE_DANGER_ANGLE)
        harness.advanceBy(seconds = 30)
        assertEquals(0, harness.overlay.level)

        harness.holdPosture(BAD_ANGLE)
        harness.advanceBy(seconds = 10)
        harness.holdPosture(EscalationConfig.POSTURE_RESET_ANGLE)
        harness.advanceBy(seconds = 30)
        assertEquals(1, harness.overlay.level)
    }

    @Test
    fun `one long tick carries the overflow across several levels`() {
        harness.reachPostureLevel1()

        harness.jumpBy(seconds = 6 * 60)

        assertEquals(3, harness.overlay.level)
        assertEquals(EscalationConfig.LEVEL_3_AUTO_HOME_SEC, harness.overlay.level3SecondsLeft)
    }

    @Test
    fun `repeated unchanged call and screen reports do not restart the posture count`() {
        harness.openApp()
        harness.holdPosture(BAD_ANGLE)

        repeat(5) {
            harness.advanceBy(seconds = 2)
            coordinator.onCallStateChanged(isInCall = false)
            coordinator.onScreenStateChanged(isScreenOn = true)
        }

        assertEquals(1, harness.overlay.level)
    }

    @Test
    fun `farewell plays for one second only when resolving posture empties the overlay`() {
        harness.reachPostureLevel1()
        harness.forgivePosture()
        assertTrue(harness.overlay.isFarewell)
        assertEquals(0, harness.overlay.level)

        harness.advanceBy(seconds = EscalationConfig.FAREWELL_SEC)
        assertFalse(harness.overlay.isFarewell)
    }
}
