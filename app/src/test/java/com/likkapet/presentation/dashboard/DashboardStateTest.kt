package com.likkapet.presentation.dashboard

import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.model.DailyStats
import com.likkapet.domain.model.LikkaSettings
import com.likkapet.domain.model.LikkaSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

/** Global states of design system §3.5 and their priority. */
class DashboardStateTest {
    private val now = 1_000_000L
    private val healthy = LikkaSnapshot(LikkaSettings(onboardingCompleted = true, likkaEnabled = true))
    private val noSignals = RuntimeSignals()

    private fun LikkaSnapshot.withSettings(transform: (LikkaSettings) -> LikkaSettings) = copy(settings = transform(settings))

    private fun status(
        snapshot: LikkaSnapshot,
        hasAllPermissions: Boolean = true,
        signals: RuntimeSignals = noSignals,
    ) = deriveDashboardStatus(snapshot, hasAllPermissions, signals, now)

    private fun ui(
        snapshot: LikkaSnapshot,
        signals: RuntimeSignals = noSignals,
    ) = buildDashboardUiState(snapshot, hasAllPermissions = true, signals, DashboardLocalState(), now, ZoneOffset.UTC)

    @Test
    fun `an enabled app with every permission and a connection is protecting`() {
        assertEquals(DashboardStatus.PROTECTING, status(healthy))
    }

    @Test
    fun `a disabled Likka wins over every other state`() {
        val snapshot = healthy.withSettings { it.copy(likkaEnabled = false, pausedUntilMillis = now + 1) }

        assertEquals(DashboardStatus.DISABLED, status(snapshot, hasAllPermissions = false, signals = RuntimeSignals(isOnline = false)))
    }

    @Test
    fun `a missing permission wins over a pause`() {
        val snapshot = healthy.withSettings { it.copy(pausedUntilMillis = now + 1) }

        assertEquals(DashboardStatus.PERMISSION_MISSING, status(snapshot, hasAllPermissions = false))
    }

    @Test
    fun `a pause is active only until its deadline`() {
        assertEquals(DashboardStatus.PAUSED, status(healthy.withSettings { it.copy(pausedUntilMillis = now + 1) }))
        assertEquals(DashboardStatus.PROTECTING, status(healthy.withSettings { it.copy(pausedUntilMillis = now) }))
    }

    @Test
    fun `no internet or no AI credit shows the offline state`() {
        assertEquals(DashboardStatus.OFFLINE, status(healthy, signals = RuntimeSignals(isOnline = false)))
        assertEquals(DashboardStatus.OFFLINE, status(healthy, signals = RuntimeSignals(hasAiCredit = false)))
    }

    @Test
    fun `with AI switched off the connectivity notice never shows`() {
        val snapshot = healthy.withSettings { it.copy(aiEnabled = false) }

        assertEquals(DashboardStatus.AI_DISABLED, status(snapshot, signals = RuntimeSignals(isOnline = false)))
    }

    @Test
    fun `every state has the pose of the design system`() {
        assertEquals(
            listOf("idle", "sit", "sleeping", "worried", "idle", "idle"),
            DashboardStatus.entries.map { it.pose.key },
        )
    }

    @Test
    fun `pause availability follows level 3 and the daily limit`() {
        val limit = EscalationConfig.MAX_PAUSES_PER_DAY
        val exhausted = healthy.copy(today = DailyStats(pauses = limit))

        assertEquals(PauseAvailability.AVAILABLE, ui(healthy).pauseAvailability)
        assertEquals(PauseAvailability.NO_PAUSES_LEFT, ui(exhausted).pauseAvailability)
        assertEquals(PauseAvailability.LEVEL_3_ACTIVE, ui(exhausted, RuntimeSignals(isLevel3Active = true)).pauseAvailability)
        assertEquals(limit - 1, ui(healthy.copy(today = DailyStats(pauses = 1))).pausesLeft)
    }

    @Test
    fun `the metrics come from today's stored counters`() {
        val snapshot = healthy.copy(today = DailyStats(usageMinutes = 23, interventions = 4, streakDays = 6))

        val state = ui(snapshot)

        assertEquals(listOf(23, 4, 6), listOf(state.minutesToday, state.interventionsToday, state.streakDays))
    }

    @Test
    fun `the pause deadline is formatted as a wall-clock time`() {
        val eighteenForty = (18 * 60 + 40) * 60_000L

        assertEquals("18:40", ui(healthy.withSettings { it.copy(pausedUntilMillis = eighteenForty) }).pausedUntilLabel)
        assertNull(ui(healthy).pausedUntilLabel)
    }

    @Test
    fun `the thirty minute option is preselected`() {
        assertEquals(30, DashboardLocalState().selectedPauseMinutes)
        assert(DashboardLocalState().selectedPauseMinutes in EscalationConfig.PAUSE_OPTIONS_MIN)
    }
}
