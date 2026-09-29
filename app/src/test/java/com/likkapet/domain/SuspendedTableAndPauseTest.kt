package com.likkapet.domain

import com.likkapet.domain.CoordinatorHarness.Companion.BAD_ANGLE
import com.likkapet.domain.model.LikkaState
import com.likkapet.domain.model.OverlayVisibility
import com.likkapet.domain.model.PauseResult
import com.likkapet.domain.model.ReasonLevel
import com.likkapet.domain.model.TriggerReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Suspended, on-table and paused as separate concepts (documentación §3.3, RF-E07, RF-O06, §4). */
class SuspendedTableAndPauseTest {
    private val harness = CoordinatorHarness()
    private val coordinator = harness.coordinator

    private fun reachPostureLevel3() {
        harness.reachPostureLevel1()
        harness.advanceBy(minutes = 5)
    }

    @Test
    fun `a call at second 12 of the level 3 countdown resumes with 8 seconds left`() {
        reachPostureLevel3()
        harness.advanceBy(seconds = 12)

        coordinator.onCallStateChanged(isInCall = true)
        harness.advanceBy(minutes = 2)
        coordinator.onCallStateChanged(isInCall = false)

        assertEquals(LikkaState.WATCHING, harness.state)
        assertEquals(8, harness.overlay.level3SecondsLeft)
        harness.advanceBy(seconds = 7)
        assertEquals(LikkaState.WATCHING, harness.state)
        harness.advanceBy(seconds = 1)
        assertEquals(LikkaState.EJECTED, harness.state)
    }

    @Test
    fun `turning the screen off at level 3 freezes the countdown`() {
        reachPostureLevel3()
        harness.advanceBy(seconds = 12)

        coordinator.onScreenStateChanged(isScreenOn = false)
        harness.advanceBy(minutes = 1)
        coordinator.onScreenStateChanged(isScreenOn = true)

        assertEquals(LikkaState.WATCHING, harness.state)
        assertEquals(8, harness.overlay.level3SecondsLeft)
    }

    @Test
    fun `a paused overlay hides during a call while the state stays paused`() {
        harness.startSessionWithGoodPosture()
        coordinator.onPauseSelected(minutes = 30, pausesUsedToday = 0)

        coordinator.onCallStateChanged(isInCall = true)

        assertEquals(LikkaState.PAUSED, harness.state)
        assertEquals(OverlayVisibility.HIDDEN_SUSPENDED, harness.overlay.visibility)
    }

    @Test
    fun `a pause is rejected while a level 3 posture track is hidden on the table`() {
        reachPostureLevel3()
        harness.putOnTable()
        assertEquals(0, harness.overlay.level)

        assertEquals(PauseResult.REJECTED_LEVEL_3, coordinator.onPauseSelected(minutes = 15, pausesUsedToday = 0))
    }

    @Test
    fun `five minutes with the screen off end the session`() {
        harness.reachPostureLevel1()
        coordinator.onScreenStateChanged(isScreenOn = false)

        harness.advanceBy(minutes = 5)
        coordinator.onScreenStateChanged(isScreenOn = true)

        assertEquals(LikkaState.WATCHING, harness.state)
        assertEquals(ReasonLevel.NONE, coordinator.trackLevel(TriggerReason.POSTURE))
        assertEquals(0, harness.overlay.level)
    }

    @Test
    fun `a long call never ends the session`() {
        harness.reachPostureLevel1()
        harness.leaveWatchedApps()
        harness.advanceBy(minutes = 1)
        coordinator.onCallStateChanged(isInCall = true)

        harness.advanceBy(minutes = 30)
        coordinator.onCallStateChanged(isInCall = false)

        assertEquals(LikkaState.WATCHING, harness.state)
        assertEquals(ReasonLevel.LEVEL_1, coordinator.trackLevel(TriggerReason.POSTURE))
    }

    @Test
    fun `leaving during a call at level 3 ejects once the call ends`() {
        reachPostureLevel3()
        coordinator.onCallStateChanged(isInCall = true)
        harness.leaveWatchedApps()
        assertEquals(LikkaState.SUSPENDED, harness.state)

        coordinator.onCallStateChanged(isInCall = false)

        assertEquals(LikkaState.EJECTED, harness.state)
    }

    @Test
    fun `the screen turning off while ejected does not stop the 5 minute window`() {
        reachPostureLevel3()
        coordinator.onSurrenderClick()
        coordinator.onScreenStateChanged(isScreenOn = false)

        harness.advanceBy(minutes = 5)

        assertEquals(LikkaState.IDLE, harness.state)
    }

    @Test
    fun `on the table only posture freezes while usage time keeps running and stays visible`() {
        harness.startSessionWithGoodPosture()
        harness.advanceBy(minutes = 15)
        harness.putOnTable()

        harness.advanceBy(minutes = 2)

        assertEquals(2, harness.overlay.level)
        assertEquals(TriggerReason.USAGE_TIME, harness.overlay.reason)
        assertEquals(OverlayVisibility.SHOWN, harness.overlay.visibility)
    }

    @Test
    fun `on the table the posture track is hidden and frozen, then resumes its remaining time`() {
        harness.reachPostureLevel1()
        harness.advanceBy(minutes = 1)
        harness.putOnTable()

        harness.advanceBy(minutes = 5)
        assertEquals(0, harness.overlay.level)
        assertEquals(ReasonLevel.LEVEL_1, coordinator.trackLevel(TriggerReason.POSTURE))

        harness.holdPosture(BAD_ANGLE)
        assertEquals(1, harness.overlay.level)
        harness.advanceBy(seconds = 59)
        assertEquals(1, harness.overlay.level)
        harness.advanceBy(seconds = 1)
        assertEquals(2, harness.overlay.level)
    }

    @Test
    fun `a flat phone on the table never triggers posture`() {
        harness.openApp()
        harness.putOnTable()

        harness.advanceBy(minutes = 1)

        assertEquals(ReasonLevel.NONE, coordinator.trackLevel(TriggerReason.POSTURE))
        assertEquals(0, harness.overlay.level)
    }

    @Test
    fun `the table freezes a level 3 countdown driven by posture`() {
        reachPostureLevel3()
        harness.advanceBy(seconds = 5)
        harness.putOnTable()

        harness.advanceBy(minutes = 1)
        assertEquals(LikkaState.WATCHING, harness.state)
        assertEquals(0, harness.overlay.level)

        harness.holdPosture(BAD_ANGLE)
        assertEquals(3, harness.overlay.level)
        assertEquals(15, harness.overlay.level3SecondsLeft)
    }

    @Test
    fun `a pause freezes both tracks and they resume with their remaining time`() {
        harness.reachPostureLevel1()
        harness.advanceBy(minutes = 1)
        coordinator.onPauseSelected(minutes = 15, pausesUsedToday = 0)

        harness.advanceBy(minutes = 15)
        assertEquals(1, harness.overlay.level)
        harness.advanceBy(seconds = 59)
        assertEquals(1, harness.overlay.level)
        harness.advanceBy(seconds = 1)
        assertEquals(2, harness.overlay.level)
    }

    @Test
    fun `paused minutes do not count towards the leisure session`() {
        harness.startSessionWithGoodPosture()
        harness.advanceBy(minutes = 14)
        coordinator.onPauseSelected(minutes = 60, pausesUsedToday = 0)
        harness.advanceBy(minutes = 60)

        harness.advanceBy(seconds = 59)
        assertEquals(0, harness.overlay.level)
        harness.advanceBy(seconds = 1)
        assertEquals(1, harness.overlay.level)
    }

    @Test
    fun `five minutes away during a pause still end the session`() {
        harness.reachPostureLevel1()
        coordinator.onPauseSelected(minutes = 30, pausesUsedToday = 0)
        harness.leaveWatchedApps()

        harness.advanceBy(minutes = 5)
        assertEquals(LikkaState.PAUSED, harness.state)
        assertEquals(ReasonLevel.NONE, coordinator.trackLevel(TriggerReason.POSTURE))

        harness.advanceBy(minutes = 25)
        assertEquals(LikkaState.IDLE, harness.state)
    }

    @Test
    fun `a pause can start from idle and returns to idle when it expires`() {
        assertEquals(PauseResult.ACCEPTED, coordinator.onPauseSelected(minutes = 60, pausesUsedToday = 0))
        assertEquals(LikkaState.PAUSED, harness.state)

        harness.advanceBy(minutes = 60)

        assertEquals(LikkaState.IDLE, harness.state)
    }

    @Test
    fun `opening a watched app during a pause from idle starts watching when the pause ends`() {
        coordinator.onPauseSelected(minutes = 15, pausesUsedToday = 0)
        harness.advanceBy(minutes = 10)
        harness.openApp()
        assertEquals(LikkaState.PAUSED, harness.state)
        assertTrue(harness.overlay.isPaused)

        harness.advanceBy(minutes = 5)

        assertEquals(LikkaState.WATCHING, harness.state)
    }

    @Test
    fun `leaving the app restarts the continuous bad posture count`() {
        harness.openApp()
        harness.holdPosture(BAD_ANGLE)
        harness.advanceBy(seconds = 6)
        harness.leaveWatchedApps()
        harness.advanceBy(seconds = 30)

        harness.openApp()
        harness.holdPosture(BAD_ANGLE)
        harness.advanceBy(seconds = 9)
        assertEquals(0, harness.overlay.level)
        harness.advanceBy(seconds = 1)
        assertEquals(1, harness.overlay.level)
    }
}
