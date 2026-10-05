package com.likkapet.service

import android.content.Intent
import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.model.DailyStats
import com.likkapet.domain.model.LikkaOverlayState
import com.likkapet.domain.model.LikkaSettings
import com.likkapet.domain.model.LikkaSnapshot
import com.likkapet.domain.model.LikkaState
import com.likkapet.domain.model.OverlayVisibility
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.TriggerReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** The Android-free decisions of the service: notification content, day checks, the home intent, log lines. */
class PureServiceRulesTest {
    private fun snapshot(pausesToday: Int = 0) = LikkaSnapshot(LikkaSettings(), DailyStats(pauses = pausesToday))

    private fun overlay(level: Int = 0) = LikkaOverlayState.NOTHING_TO_SHOW.copy(level = level)

    private fun notification(
        state: LikkaState = LikkaState.WATCHING,
        level: Int = 0,
        pausesToday: Int = 0,
        permissions: Boolean = true,
    ) = GuardianNotificationState.of(state, overlay(level), snapshot(pausesToday), permissions)

    @Test
    fun `the notification offers the pause when rule E allows it`() {
        assertEquals(GuardianNotificationState(GuardianMode.PROTECTING, canPause = true), notification())
        assertTrue(notification(level = 2).canPause)
    }

    @Test
    fun `the pause action is hidden at level 3, while ejected, while paused and with no pauses left`() {
        assertFalse(notification(level = 3).canPause)
        assertFalse(notification(state = LikkaState.EJECTED).canPause)
        assertFalse(notification(state = LikkaState.PAUSED).canPause)
        assertFalse(notification(pausesToday = EscalationConfig.MAX_PAUSES_PER_DAY).canPause)
    }

    @Test
    fun `a missing permission wins over everything and hides the pause`() {
        val state = notification(state = LikkaState.PAUSED, permissions = false)

        assertEquals(GuardianNotificationState(GuardianMode.NEEDS_PERMISSION, canPause = false), state)
    }

    @Test
    fun `a pause shows the paused text`() {
        assertEquals(GuardianMode.PAUSED, notification(state = LikkaState.PAUSED).mode)
    }

    @Test
    fun `the next day check is at midnight when that is sooner than the maximum wait`() {
        val zone = ZoneOffset.UTC
        val justBeforeMidnight = ZonedDateTime.of(2026, 10, 5, 23, 59, 30, 0, zone).toInstant().toEpochMilli()
        val noon = ZonedDateTime.of(2026, 10, 5, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(30_000L, DayRollover.nextCheckDelayMillis(justBeforeMidnight, zone, maxWaitSec = 60))
        assertEquals(60_000L, DayRollover.nextCheckDelayMillis(noon, zone, maxWaitSec = 60))
    }

    @Test
    fun `midnight is computed in the current zone`() {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val at2300Utc = ZonedDateTime.of(2026, 10, 5, 23, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

        // 20:00 in Buenos Aires: four hours to midnight there, not one.
        assertEquals(4 * 3_600_000L, DayRollover.millisUntilNextDay(at2300Utc, zone))
    }

    @Test
    fun `the home intent is the launcher intent in a new task`() {
        assertEquals("android.intent.action.MAIN", HomeLauncher.ACTION)
        assertEquals("android.intent.category.HOME", HomeLauncher.CATEGORY)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, HomeLauncher.FLAGS)
    }

    @Test
    fun `a state line carries enum names and numbers only`() {
        val line =
            StateChangeLog.format(
                StateChangeLog.entryOf(
                    LikkaState.WATCHING,
                    overlay(level = 2).copy(reason = TriggerReason.POSTURE, app = TargetApp.TIKTOK, roast = "texto de la ia"),
                ),
            )

        assertEquals("State: global=WATCHING level=2 reason=POSTURE visibility=SHOWN paused=false farewell=false", line)
        assertFalse("TIKTOK" in line || "texto" in line)
    }

    @Test
    fun `the countdown seconds do not make a new log entry`() {
        val a = StateChangeLog.entryOf(LikkaState.WATCHING, overlay(3).copy(level3SecondsLeft = 20))
        val b = StateChangeLog.entryOf(LikkaState.WATCHING, overlay(3).copy(level3SecondsLeft = 19))

        assertEquals(a, b)
        assertEquals(OverlayVisibility.SHOWN.name, a.visibility)
    }
}
