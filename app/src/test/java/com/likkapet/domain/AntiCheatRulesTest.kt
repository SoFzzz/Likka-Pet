package com.likkapet.domain

import com.likkapet.domain.CoordinatorHarness.Companion.BAD_ANGLE
import com.likkapet.domain.model.LikkaState
import com.likkapet.domain.model.PauseResult
import com.likkapet.domain.model.ReasonLevel
import com.likkapet.domain.model.TriggerReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules A–E of documentación §3.2 (RF-E02–RF-E06). */
class AntiCheatRulesTest {
    private val harness = CoordinatorHarness()
    private val coordinator = harness.coordinator

    // Usage time at Level 1 from 15:00; posture Level 1 at 15:10 and Level 2 at 17:10.
    private fun reachUsageLevel2WithPostureLevel2() {
        harness.startSessionWithGoodPosture()
        harness.advanceBy(minutes = 15)
        harness.holdPosture(BAD_ANGLE)
        harness.advanceBy(minutes = 2, seconds = 10)
    }

    // Posture Level 1 at 0:10, Level 2 at 2:10, forgiven at 2:25 with usage time inactive.
    private fun resolvePostureFromLevel2WithGrace() {
        harness.reachPostureLevel1()
        harness.advanceBy(minutes = 2)
        harness.forgivePosture()
    }

    @Test
    fun `rule A - straightening up resolves only posture and keeps the usage time level`() {
        reachUsageLevel2WithPostureLevel2()
        assertEquals(2, harness.overlay.level)

        harness.forgivePosture()

        assertEquals(ReasonLevel.NONE, coordinator.trackLevel(TriggerReason.POSTURE))
        assertEquals(2, harness.overlay.level)
        assertEquals(TriggerReason.USAGE_TIME, harness.overlay.reason)
        assertFalse(harness.overlay.isFarewell)
    }

    @Test
    fun `rule A - good posture never resolves usage time`() {
        harness.startSessionWithGoodPosture()
        harness.advanceBy(minutes = 15)

        harness.advanceBy(minutes = 5)

        assertEquals(3, harness.overlay.level)
        assertEquals(TriggerReason.USAGE_TIME, harness.overlay.reason)
    }

    @Test
    fun `rule B - after resolving posture there are 60 seconds without posture detection`() {
        harness.reachPostureLevel1()
        harness.forgivePosture()
        harness.holdPosture(BAD_ANGLE)

        harness.advanceBy(seconds = EscalationConfig.GRACE_AFTER_RESET_SEC)
        assertEquals(0, harness.overlay.level)

        harness.advanceBy(seconds = 9)
        assertEquals(0, harness.overlay.level)
        harness.advanceBy(seconds = 1)
        assertEquals(1, harness.overlay.level)
    }

    @Test
    fun `rule B - resolving posture while usage time is active gives no grace`() {
        reachUsageLevel2WithPostureLevel2()
        harness.forgivePosture()
        harness.holdPosture(BAD_ANGLE)

        harness.advanceBy(seconds = 9)
        assertEquals(ReasonLevel.NONE, coordinator.trackLevel(TriggerReason.POSTURE))
        harness.advanceBy(seconds = 1)

        // Triggered right away; which level it resumes is rule C's business.
        assertTrue(coordinator.trackLevel(TriggerReason.POSTURE) != ReasonLevel.NONE)
    }

    @Test
    fun `rule B - ending usage time goes straight to idle without grace`() {
        harness.startSessionWithGoodPosture()
        harness.advanceBy(minutes = 15)
        harness.leaveWatchedApps()
        harness.advanceBy(minutes = 5)
        assertEquals(LikkaState.IDLE, harness.state)

        harness.openApp()
        harness.holdPosture(BAD_ANGLE)
        harness.advanceBy(seconds = 10)

        assertEquals(1, harness.overlay.level)
        assertEquals(TriggerReason.POSTURE, harness.overlay.reason)
    }

    @Test
    fun `rule C - relapsing 5 minutes after grace resumes the previous posture level`() {
        resolvePostureFromLevel2WithGrace()
        harness.advanceBy(minutes = 4, seconds = 50)
        harness.holdPosture(BAD_ANGLE)

        harness.advanceBy(seconds = 10)

        assertEquals(2, harness.overlay.level)
        assertEquals(TriggerReason.POSTURE, harness.overlay.reason)
    }

    @Test
    fun `rule C - relapsing 11 minutes after resolving starts again at level 1`() {
        resolvePostureFromLevel2WithGrace()
        harness.advanceBy(minutes = 10, seconds = 50)
        harness.holdPosture(BAD_ANGLE)

        harness.advanceBy(seconds = 10)

        assertEquals(1, harness.overlay.level)
    }

    @Test
    fun `rule C - relapse applies without grace and counts from the posture resolution`() {
        reachUsageLevel2WithPostureLevel2()
        harness.forgivePosture()
        harness.advanceBy(minutes = 1)
        harness.holdPosture(BAD_ANGLE)

        harness.advanceBy(seconds = 9)
        assertEquals(ReasonLevel.NONE, coordinator.trackLevel(TriggerReason.POSTURE))
        harness.advanceBy(seconds = 1)
        assertEquals(ReasonLevel.LEVEL_2, coordinator.trackLevel(TriggerReason.POSTURE))
    }

    @Test
    fun `rule C - relapse after resolving from level 3 resumes at level 3`() {
        harness.reachPostureLevel1()
        harness.advanceBy(minutes = 5)
        assertEquals(3, harness.overlay.level)
        harness.forgivePosture()
        assertEquals(LikkaState.WATCHING, harness.state)

        harness.advanceBy(minutes = 2)
        harness.holdPosture(BAD_ANGLE)
        harness.advanceBy(seconds = 10)

        assertEquals(3, harness.overlay.level)
        assertEquals(EscalationConfig.LEVEL_3_AUTO_HOME_SEC, harness.overlay.level3SecondsLeft)
    }

    @Test
    fun `rule D - reopening within 5 minutes of ejection resumes both active tracks at level 2`() {
        // Usage time reaches Level 3 at 20:00, posture at 20:10, auto-home at 20:20.
        reachUsageLevel2WithPostureLevel2()
        harness.advanceBy(minutes = 3)
        assertEquals(ReasonLevel.LEVEL_3, coordinator.trackLevel(TriggerReason.POSTURE))
        assertEquals(ReasonLevel.LEVEL_3, coordinator.trackLevel(TriggerReason.USAGE_TIME))
        harness.advanceBy(seconds = 10)
        assertEquals(LikkaState.EJECTED, harness.state)
        harness.leaveWatchedApps()

        harness.advanceBy(minutes = 3)
        harness.openApp()

        assertEquals(LikkaState.WATCHING, harness.state)
        assertEquals(ReasonLevel.LEVEL_2, coordinator.trackLevel(TriggerReason.POSTURE))
        assertEquals(ReasonLevel.LEVEL_2, coordinator.trackLevel(TriggerReason.USAGE_TIME))
    }

    @Test
    fun `rule D - only the tracks still active at ejection come back`() {
        harness.reachPostureLevel1()
        harness.advanceBy(minutes = 5)
        harness.surrenderAndGoHome()
        assertEquals(LikkaState.EJECTED, harness.state)

        harness.advanceBy(minutes = 3)
        harness.openApp()

        assertEquals(ReasonLevel.LEVEL_2, coordinator.trackLevel(TriggerReason.POSTURE))
        assertEquals(ReasonLevel.NONE, coordinator.trackLevel(TriggerReason.USAGE_TIME))
    }

    @Test
    fun `rule D - a reason already resolved before ejection is not reactivated`() {
        harness.startSessionWithGoodPosture()
        harness.advanceBy(minutes = 16)
        harness.holdPosture(BAD_ANGLE)
        harness.advanceBy(seconds = 10)
        harness.forgivePosture()
        harness.advanceBy(minutes = 3, seconds = 35)
        assertEquals(3, harness.overlay.level)
        harness.surrenderAndGoHome()

        harness.advanceBy(minutes = 2)
        harness.openApp()

        assertEquals(ReasonLevel.NONE, coordinator.trackLevel(TriggerReason.POSTURE))
        assertEquals(ReasonLevel.LEVEL_2, coordinator.trackLevel(TriggerReason.USAGE_TIME))
    }

    @Test
    fun `rule D - a track at level 1 when ejected also comes back at level 2`() {
        harness.startSessionWithGoodPosture()
        harness.advanceBy(minutes = 19, seconds = 50)
        harness.holdPosture(BAD_ANGLE)
        harness.advanceBy(seconds = 10)
        assertEquals(ReasonLevel.LEVEL_3, coordinator.trackLevel(TriggerReason.USAGE_TIME))
        assertEquals(ReasonLevel.LEVEL_1, coordinator.trackLevel(TriggerReason.POSTURE))
        harness.surrenderAndGoHome()

        harness.advanceBy(minutes = 1)
        harness.openApp()

        assertEquals(ReasonLevel.LEVEL_2, coordinator.trackLevel(TriggerReason.POSTURE))
        assertEquals(ReasonLevel.LEVEL_2, coordinator.trackLevel(TriggerReason.USAGE_TIME))
    }

    @Test
    fun `rule D - reopening after 5 minutes starts a new session from zero`() {
        harness.reachPostureLevel1()
        harness.advanceBy(minutes = 5)
        harness.surrenderAndGoHome()

        harness.advanceBy(minutes = 5)
        assertEquals(LikkaState.IDLE, harness.state)
        harness.openApp()

        assertEquals(LikkaState.WATCHING, harness.state)
        assertEquals(ReasonLevel.NONE, coordinator.trackLevel(TriggerReason.POSTURE))
        assertEquals(0, harness.overlay.level)
    }

    @Test
    fun `rule E - the fourth pause of the day is rejected`() {
        harness.startSessionWithGoodPosture()

        assertEquals(PauseResult.REJECTED_DAILY_LIMIT, coordinator.onPauseSelected(minutes = 15, pausesUsedToday = 3))
        assertEquals(LikkaState.WATCHING, harness.state)
        assertEquals(PauseResult.ACCEPTED, coordinator.onPauseSelected(minutes = 15, pausesUsedToday = 2))
        assertEquals(LikkaState.PAUSED, harness.state)
    }

    @Test
    fun `rule E - a pause is rejected while any track is at level 3`() {
        harness.reachPostureLevel1()
        harness.advanceBy(minutes = 5)

        assertEquals(PauseResult.REJECTED_LEVEL_3, coordinator.onPauseSelected(minutes = 30, pausesUsedToday = 0))
        assertEquals(LikkaState.WATCHING, harness.state)
    }

    @Test
    fun `rule E - a second pause while paused is rejected`() {
        harness.startSessionWithGoodPosture()
        coordinator.onPauseSelected(minutes = 15, pausesUsedToday = 0)

        assertEquals(PauseResult.REJECTED_ALREADY_PAUSED, coordinator.onPauseSelected(minutes = 60, pausesUsedToday = 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rule E - a pause length outside the offered options is a programming error`() {
        coordinator.onPauseSelected(minutes = 45, pausesUsedToday = 0)
    }

    @Test
    fun `rule E - a pause is rejected while ejected`() {
        harness.reachPostureLevel1()
        harness.advanceBy(minutes = 5)
        coordinator.onSurrenderClick()

        assertEquals(PauseResult.REJECTED_WHILE_EJECTED, coordinator.onPauseSelected(minutes = 30, pausesUsedToday = 0))
    }
}
