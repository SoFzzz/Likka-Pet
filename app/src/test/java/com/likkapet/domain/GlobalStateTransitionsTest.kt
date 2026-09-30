package com.likkapet.domain

import com.likkapet.domain.model.LikkaState
import com.likkapet.domain.model.OverlayVisibility
import com.likkapet.domain.model.PauseResult
import com.likkapet.domain.model.ReasonLevel
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.TriggerReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Transitions of the global state machine in documentación §3.3, plus leaving a watched app. */
class GlobalStateTransitionsTest {
    private val harness = CoordinatorHarness()
    private val coordinator = harness.coordinator

    private fun reachPostureLevel3() {
        harness.reachPostureLevel1()
        harness.advanceBy(minutes = 5)
    }

    @Test
    fun `idle goes to watching when a watched app opens`() {
        assertEquals(LikkaState.IDLE, harness.state)

        harness.openApp(TargetApp.YOUTUBE)

        assertEquals(LikkaState.WATCHING, harness.state)
        assertEquals(TargetApp.YOUTUBE, harness.overlay.app)
        assertEquals(OverlayVisibility.SHOWN, harness.overlay.visibility)
    }

    @Test
    fun `watching goes to idle after 5 minutes away from all watched apps`() {
        harness.startSessionWithGoodPosture()
        harness.leaveWatchedApps()

        harness.advanceBy(minutes = 4, seconds = 59)
        assertEquals(LikkaState.WATCHING, harness.state)
        harness.advanceBy(seconds = 1)
        assertEquals(LikkaState.IDLE, harness.state)
    }

    @Test
    fun `surrender at level 3 ejects but is ignored below level 3`() {
        harness.reachPostureLevel1()
        harness.advanceBy(minutes = 2)
        coordinator.onSurrenderClick()
        assertEquals(LikkaState.WATCHING, harness.state)

        harness.advanceBy(minutes = 3)
        coordinator.onSurrenderClick()

        assertEquals(LikkaState.EJECTED, harness.state)
        assertEquals(0, harness.overlay.level)
    }

    @Test
    fun `level 3 ejects automatically after the 20 second countdown`() {
        reachPostureLevel3()

        harness.advanceBy(seconds = 19)
        assertEquals(LikkaState.WATCHING, harness.state)
        assertEquals(1, harness.overlay.level3SecondsLeft)

        harness.advanceBy(seconds = 1)
        assertEquals(LikkaState.EJECTED, harness.state)
    }

    @Test
    fun `leaving the watched app at global level 3 is an ejection, not hidden while away`() {
        reachPostureLevel3()

        harness.leaveWatchedApps()

        assertEquals(LikkaState.EJECTED, harness.state)
        assertEquals(OverlayVisibility.SHOWN, harness.overlay.visibility)
        assertEquals(0, harness.overlay.level)
    }

    @Test
    fun `leaving the watched app at level 1 or 2 hides the overlay and freezes both tracks`() {
        harness.reachPostureLevel1()
        harness.advanceBy(minutes = 2)
        harness.leaveWatchedApps()

        harness.advanceBy(minutes = 4)

        assertEquals(LikkaState.WATCHING, harness.state)
        assertEquals(OverlayVisibility.HIDDEN_WHILE_AWAY, harness.overlay.visibility)
        assertEquals(2, harness.overlay.level)
    }

    @Test
    fun `coming back before 5 minutes resumes the same level and remaining time without rule D`() {
        harness.reachPostureLevel1()
        harness.advanceBy(minutes = 1)
        harness.leaveWatchedApps()
        harness.advanceBy(minutes = 4)

        harness.openApp()
        assertEquals(OverlayVisibility.SHOWN, harness.overlay.visibility)
        assertEquals(1, harness.overlay.level)

        harness.advanceBy(seconds = 59)
        assertEquals(1, harness.overlay.level)
        harness.advanceBy(seconds = 1)
        assertEquals(2, harness.overlay.level)
    }

    @Test
    fun `a stale report of the watched app right after ejection is not a return`() {
        reachPostureLevel3()
        coordinator.onSurrenderClick()

        harness.advanceBy(seconds = 2)
        harness.openApp()
        assertEquals(LikkaState.EJECTED, harness.state)

        harness.leaveWatchedApps()
        harness.advanceBy(minutes = 1)
        harness.openApp()
        assertEquals(LikkaState.WATCHING, harness.state)
        assertEquals(ReasonLevel.LEVEL_2, coordinator.trackLevel(TriggerReason.POSTURE))
    }

    @Test
    fun `a watched app still in front after the exit timeout counts as a return with rule D`() {
        reachPostureLevel3()
        coordinator.onSurrenderClick()

        harness.advanceBy(seconds = EscalationConfig.EJECTION_EXIT_TIMEOUT_SEC)

        assertEquals(LikkaState.WATCHING, harness.state)
        assertEquals(ReasonLevel.LEVEL_2, coordinator.trackLevel(TriggerReason.POSTURE))
        assertEquals(2, harness.overlay.level)
    }

    @Test
    fun `a watched app still in front just before the exit timeout stays ejected`() {
        reachPostureLevel3()
        coordinator.onSurrenderClick()

        harness.advanceBy(seconds = EscalationConfig.EJECTION_EXIT_TIMEOUT_SEC - 1)
        harness.openApp()

        assertEquals(LikkaState.EJECTED, harness.state)
        assertEquals(0, harness.overlay.level)
    }

    @Test
    fun `ejected goes to idle after 5 minutes away`() {
        reachPostureLevel3()
        harness.surrenderAndGoHome()

        harness.advanceBy(minutes = 4, seconds = 59)
        assertEquals(LikkaState.EJECTED, harness.state)
        harness.advanceBy(seconds = 1)
        assertEquals(LikkaState.IDLE, harness.state)
    }

    @Test
    fun `a call suspends watching and each track resumes its level and remaining time`() {
        harness.reachPostureLevel1()
        harness.advanceBy(minutes = 1)

        coordinator.onCallStateChanged(isInCall = true)
        harness.advanceBy(minutes = 10)
        assertEquals(LikkaState.SUSPENDED, harness.state)
        assertEquals(OverlayVisibility.HIDDEN_SUSPENDED, harness.overlay.visibility)

        coordinator.onCallStateChanged(isInCall = false)
        assertEquals(LikkaState.WATCHING, harness.state)
        harness.advanceBy(seconds = 59)
        assertEquals(1, harness.overlay.level)
        harness.advanceBy(seconds = 1)
        assertEquals(2, harness.overlay.level)
    }

    @Test
    fun `turning the screen off suspends watching and turning it on resumes`() {
        harness.reachPostureLevel1()

        coordinator.onScreenStateChanged(isScreenOn = false)
        harness.advanceBy(minutes = 4)
        assertEquals(LikkaState.SUSPENDED, harness.state)

        coordinator.onScreenStateChanged(isScreenOn = true)
        assertEquals(LikkaState.WATCHING, harness.state)
        assertEquals(1, harness.overlay.level)
    }

    @Test
    fun `watching goes to paused and back to watching when the pause expires`() {
        harness.reachPostureLevel1()

        assertEquals(PauseResult.ACCEPTED, coordinator.onPauseSelected(minutes = 15, pausesUsedToday = 0))
        assertEquals(LikkaState.PAUSED, harness.state)
        assertTrue(harness.overlay.isPaused)
        assertEquals(0, harness.overlay.level)

        harness.advanceBy(minutes = 15)
        assertEquals(LikkaState.WATCHING, harness.state)
        assertEquals(1, harness.overlay.level)
    }

    @Test
    fun `resuming manually ends the pause early`() {
        harness.startSessionWithGoodPosture()
        coordinator.onPauseSelected(minutes = EscalationConfig.NOTIFICATION_PAUSE_MIN, pausesUsedToday = 0)
        harness.advanceBy(minutes = 1)

        coordinator.onResumeClick()

        assertEquals(LikkaState.WATCHING, harness.state)
    }

    @Test
    fun `a track in level 3 keeps its reason while the countdown runs`() {
        reachPostureLevel3()

        assertEquals(TriggerReason.POSTURE, harness.overlay.reason)
        assertEquals(ReasonLevel.LEVEL_3, coordinator.trackLevel(TriggerReason.POSTURE))
    }
}
