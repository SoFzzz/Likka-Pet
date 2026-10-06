package com.likkapet.domain

import com.likkapet.domain.model.MotionMode
import com.likkapet.domain.model.MotionPose
import com.likkapet.domain.model.OverlayScene
import com.likkapet.domain.model.PauseResult
import com.likkapet.domain.model.TargetApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * What the overlay shows at each moment (RF-O02–RF-O07), from the real coordinator and planner on
 * one virtual clock: levels, the Level 3 countdown, SUSPENDED and away freezes, farewell and pause.
 */
class OverlaySceneTest {
    private val rig = OverlaySceneRig()
    private val harness get() = rig.harness

    @Test
    fun `nothing to show withdraws the window`() {
        assertEquals(OverlayScene.Withdrawn, rig.scene())
        harness.startSessionWithGoodPosture()
        assertEquals(OverlayScene.Withdrawn, rig.scene())
    }

    @Test
    fun `level 1 is Likka on an edge with the roast`() {
        harness.reachPostureLevel1()
        val scene = rig.companion()
        assertEquals(1, scene.level)
        assertEquals(MotionMode.ON_EDGE, checkNotNull(scene.motion).mode)
        assertTrue(scene.motion?.pose == MotionPose.PEEK || scene.motion?.pose == MotionPose.PERCH)
    }

    @Test
    fun `level 2 is Likka placed for its walk`() {
        reachPostureLevel2()
        val scene = rig.companion()
        assertEquals(2, scene.level)
        assertEquals(MotionMode.WALKING, scene.motion?.mode)
    }

    @Test
    fun `level 3 is the panel with the full countdown, which ticks with the clock`() {
        reachPostureLevel3()
        assertEquals(OverlayScene.Fury(roast = null, secondsLeft = EscalationConfig.LEVEL_3_AUTO_HOME_SEC), rig.scene())
        harness.advanceBy(seconds = 8)
        assertEquals(12, rig.fury().secondsLeft)
    }

    @Test
    fun `a call at second 12 hides the panel and hanging up brings it back at second 12`() {
        reachPostureLevel3()
        harness.advanceBy(seconds = 8)
        harness.coordinator.onCallStateChanged(true)
        assertEquals(OverlayScene.Hidden, rig.scene())
        harness.advanceBy(minutes = 2)
        assertEquals(OverlayScene.Hidden, rig.scene())
        harness.coordinator.onCallStateChanged(false)
        assertEquals(12, rig.fury().secondsLeft)
    }

    @Test
    fun `a call screen covering the app at level 3 hides it, and the panel comes back with the same time`() {
        reachPostureLevel3()
        harness.advanceBy(seconds = 8)
        harness.coordinator.onForegroundStateChanged(app = null, isInCall = true)
        assertEquals(OverlayScene.Hidden, rig.scene())
        harness.advanceBy(seconds = 40)
        harness.coordinator.onForegroundStateChanged(app = TargetApp.TIKTOK, isInCall = false)
        assertEquals(12, rig.fury().secondsLeft)
    }

    @Test
    fun `hanging up and staying away from the watched app at level 3 ejects once the grace runs out`() {
        reachPostureLevel3()
        harness.coordinator.onForegroundStateChanged(app = null, isInCall = true)
        harness.advanceBy(seconds = 20)
        harness.coordinator.onForegroundStateChanged(app = null, isInCall = false)
        harness.advanceBy(seconds = EscalationConfig.CALL_END_GRACE_SEC - 1)
        assertEquals(OverlayScene.Hidden, rig.scene())
        // No poll needed: the tick that ends the grace is the exit, and the user is not sent home.
        harness.advanceBy(seconds = 1)
        assertEquals(OverlayScene.Ejected(sendsHome = false), rig.scene())
    }

    @Test
    fun `the grace ends when the watched app is back, so leaving later is an immediate ejection`() {
        reachPostureLevel3()
        harness.coordinator.onForegroundStateChanged(app = null, isInCall = true)
        harness.coordinator.onForegroundStateChanged(app = null, isInCall = false)
        harness.coordinator.onForegroundStateChanged(app = TargetApp.TIKTOK, isInCall = false)
        assertEquals(EscalationConfig.LEVEL_3_AUTO_HOME_SEC, rig.fury().secondsLeft)
        harness.leaveWatchedApps()
        assertEquals(OverlayScene.Ejected(sendsHome = false), rig.scene())
    }

    @Test
    fun `hanging up with the dialer still in front keeps level 3 frozen until the watched app is back`() {
        reachPostureLevel3()
        harness.advanceBy(seconds = 8)
        harness.coordinator.onForegroundStateChanged(app = null, isInCall = true)
        harness.advanceBy(seconds = 30)
        // AudioManager reports the call over, but the dialer in front still counts as a call (§4.2).
        harness.coordinator.onForegroundStateChanged(app = null, isInCall = true)
        harness.advanceBy(seconds = 3)
        assertEquals(OverlayScene.Hidden, rig.scene())
        harness.coordinator.onForegroundStateChanged(app = TargetApp.TIKTOK, isInCall = false)
        assertEquals(12, rig.fury().secondsLeft)
    }

    // M4 with WhatsApp: the VoIP app is neither watched nor a known dialer, so right after hanging up
    // the poll sees "no watched app, no call". The grace (CALL_END_GRACE_SEC) keeps that frozen.
    @Test
    fun `hanging up a VoIP call with its app still in front comes back to level 3 with the same time`() {
        reachPostureLevel3()
        harness.advanceBy(seconds = 8)
        harness.coordinator.onForegroundStateChanged(app = null, isInCall = true)
        harness.advanceBy(seconds = 30)
        harness.coordinator.onForegroundStateChanged(app = null, isInCall = false)
        assertEquals(OverlayScene.Hidden, rig.scene())
        harness.advanceBy(seconds = 2)
        harness.coordinator.onForegroundStateChanged(app = TargetApp.TIKTOK, isInCall = false)
        assertEquals(12, rig.fury().secondsLeft)
    }

    @Test
    fun `the screen off freezes the countdown too`() {
        reachPostureLevel3()
        harness.advanceBy(seconds = 5)
        harness.coordinator.onScreenStateChanged(false)
        assertEquals(OverlayScene.Hidden, rig.scene())
        harness.advanceBy(seconds = 30)
        harness.coordinator.onScreenStateChanged(true)
        assertEquals(15, rig.fury().secondsLeft)
    }

    @Test
    fun `the end of the countdown ejects and sends the user home`() {
        reachPostureLevel3()
        harness.advanceBy(seconds = EscalationConfig.LEVEL_3_AUTO_HOME_SEC)
        assertEquals(OverlayScene.Ejected(sendsHome = true), rig.scene())
    }

    @Test
    fun `surrendering ejects and sends the user home`() {
        reachPostureLevel3()
        harness.coordinator.onSurrenderClick()
        assertEquals(OverlayScene.Ejected(sendsHome = true), rig.scene())
    }

    @Test
    fun `leaving the watched app at level 3 ejects too, without opening home over the other app`() {
        reachPostureLevel3()
        harness.leaveWatchedApps()
        assertEquals(OverlayScene.Ejected(sendsHome = false), rig.scene())
    }

    @Test
    fun `leaving the watched app at level 2 hides and freezes, coming back resumes level 2`() {
        reachPostureLevel2()
        val before = rig.companion()
        harness.leaveWatchedApps()
        assertEquals(OverlayScene.Hidden, rig.scene())
        harness.advanceBy(minutes = EscalationConfig.LEVEL_2_TO_3_MIN + 1)
        assertEquals(OverlayScene.Hidden, rig.scene())
        harness.openApp()
        val after = rig.companion()
        assertEquals(2, after.level)
        assertEquals(before.motion?.x to before.motion?.y, after.motion?.x to after.motion?.y)
    }

    @Test
    fun `resolving level 1 says goodbye where Likka was, then withdraws`() {
        harness.reachPostureLevel1()
        val spot = checkNotNull(rig.companion().motion)
        harness.forgivePosture()
        val farewell = rig.resting()
        assertEquals(MotionPose.GOODBYE, farewell.pose)
        assertEquals(1, farewell.sizeLevel)
        assertEquals(spot.x to spot.y, farewell.motion?.x to farewell.motion?.y)
        harness.advanceBy(seconds = EscalationConfig.FAREWELL_SEC)
        assertEquals(OverlayScene.Withdrawn, rig.scene())
    }

    @Test
    fun `the farewell after level 2 keeps the level 2 size`() {
        reachPostureLevel2()
        rig.scene()
        harness.forgivePosture()
        assertEquals(2, rig.resting().sizeLevel)
    }

    @Test
    fun `the farewell after the level 3 panel is placed even with no previous spot`() {
        reachPostureLevel3()
        rig.scene()
        harness.forgivePosture()
        val farewell = rig.resting()
        assertEquals(MotionPose.GOODBYE, farewell.pose)
        assertEquals(2, farewell.sizeLevel)
        assertNotNull(farewell.motion)
    }

    @Test
    fun `posture resolved with usage time active shows no farewell`() {
        harness.startSessionWithGoodPosture()
        harness.advanceBy(minutes = EscalationConfig.USAGE_THRESHOLD_MIN)
        harness.holdPosture(CoordinatorHarness.BAD_ANGLE)
        harness.advanceBy(seconds = EscalationConfig.POSTURE_TRIGGER_SEC)
        rig.scene()
        harness.forgivePosture()
        assertEquals(1, rig.companion().level)
    }

    @Test
    fun `a pause sits where Likka was and resuming brings level 1 back`() {
        harness.reachPostureLevel1()
        val spot = checkNotNull(rig.companion().motion)
        assertEquals(PauseResult.ACCEPTED, harness.coordinator.onPauseSelected(PAUSE_MIN, pausesUsedToday = 0))
        val pause = rig.resting()
        assertEquals(MotionPose.SIT, pause.pose)
        assertEquals(1, pause.sizeLevel)
        assertEquals(spot.x to spot.y, pause.motion?.x to pause.motion?.y)
        harness.coordinator.onResumeClick()
        assertEquals(1, rig.companion().level)
    }

    @Test
    fun `a pause started before any level is placed once a watched app opens`() {
        assertEquals(PauseResult.ACCEPTED, harness.coordinator.onPauseSelected(PAUSE_MIN, pausesUsedToday = 0))
        assertEquals(OverlayScene.Hidden, rig.scene())
        harness.openApp()
        val pause = rig.resting()
        assertEquals(MotionPose.SIT, pause.pose)
        assertNotNull(pause.motion)
    }

    @Test
    fun `a pause hides during a call`() {
        harness.startSessionWithGoodPosture()
        harness.coordinator.onPauseSelected(PAUSE_MIN, pausesUsedToday = 0)
        harness.coordinator.onCallStateChanged(true)
        assertEquals(OverlayScene.Hidden, rig.scene())
    }

    @Test
    fun `coming back after an ejection is a fresh level 2 window`() {
        reachPostureLevel3()
        harness.surrenderAndGoHome()
        assertTrue(rig.scene() is OverlayScene.Ejected)
        harness.openApp()
        assertEquals(2, rig.companion().level)
    }

    private fun reachPostureLevel2() {
        harness.reachPostureLevel1()
        rig.scene()
        harness.advanceBy(minutes = EscalationConfig.LEVEL_1_TO_2_MIN)
    }

    private fun reachPostureLevel3() {
        reachPostureLevel2()
        rig.scene()
        harness.advanceBy(minutes = EscalationConfig.LEVEL_2_TO_3_MIN)
    }

    private companion object {
        const val PAUSE_MIN = 15
    }
}

/** The coordinator, the planner and the reducer wired the way the service wires them, on one virtual clock. */
class OverlaySceneRig {
    val harness = CoordinatorHarness()
    private val planner = OverlayMotionPlanner(harness.clock, Random(PlannerHarness.DEFAULT_SEED))
    private val reducer = OverlaySceneReducer()

    init {
        planner.onGeometryChanged(PlannerHarness.PHONE)
    }

    /** Feeds the current state to the planner, then reduces; call it at every moment that matters. */
    fun scene(): OverlayScene {
        val state = harness.overlay
        planner.onOverlayStateChanged(state)
        return reducer.sceneFor(harness.state, state, planner.motion.value)
    }

    fun companion(): OverlayScene.Companion = scene() as OverlayScene.Companion

    fun fury(): OverlayScene.Fury = scene() as OverlayScene.Fury

    fun resting(): OverlayScene.Resting = scene() as OverlayScene.Resting
}
