package com.likkapet.presentation.dashboard

import com.likkapet.domain.EscalationConfig
import com.likkapet.presentation.state.AppPermission
import com.likkapet.presentation.state.FakeAppState
import com.likkapet.presentation.state.PermissionStatus
import com.likkapet.presentation.state.pendingPermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

/** Global states of design system §3.5 and their priority. */
class DashboardStateTest {
    private val now = 1_000_000L
    private val granted =
        pendingPermissions(includeNotifications = true).mapValues { PermissionStatus.GRANTED }
    private val healthy = FakeAppState(onboardingCompleted = true, likkaEnabled = true, permissions = granted)

    private fun status(state: FakeAppState) = deriveDashboardStatus(state, now)

    @Test
    fun `an enabled app with every permission and a connection is protecting`() {
        assertEquals(DashboardStatus.PROTECTING, status(healthy))
    }

    @Test
    fun `a disabled Likka wins over every other state`() {
        val state =
            healthy.copy(
                likkaEnabled = false,
                permissions = granted + (AppPermission.OVERLAY to PermissionStatus.DENIED),
                pausedUntilMillis = now + 1,
                isOnline = false,
            )
        assertEquals(DashboardStatus.DISABLED, status(state))
    }

    @Test
    fun `a revoked permission wins over a pause`() {
        val state =
            healthy.copy(
                permissions = granted + (AppPermission.USAGE_STATS to PermissionStatus.DENIED),
                pausedUntilMillis = now + 1,
            )
        assertEquals(DashboardStatus.PERMISSION_MISSING, status(state))
    }

    @Test
    fun `a pending permission also counts as missing`() {
        val state = healthy.copy(permissions = granted + (AppPermission.NOTIFICATIONS to PermissionStatus.PENDING))
        assertEquals(DashboardStatus.PERMISSION_MISSING, status(state))
    }

    @Test
    fun `a pause is active only until its deadline`() {
        assertEquals(DashboardStatus.PAUSED, status(healthy.copy(pausedUntilMillis = now + 1)))
        assertEquals(DashboardStatus.PROTECTING, status(healthy.copy(pausedUntilMillis = now)))
    }

    @Test
    fun `no internet or no AI credit shows the offline state`() {
        assertEquals(DashboardStatus.OFFLINE, status(healthy.copy(isOnline = false)))
        assertEquals(DashboardStatus.OFFLINE, status(healthy.copy(hasAiCredit = false)))
    }

    @Test
    fun `with AI switched off the connectivity notice never shows`() {
        assertEquals(DashboardStatus.AI_DISABLED, status(healthy.copy(aiEnabled = false, isOnline = false)))
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
        val ui = { state: FakeAppState -> buildDashboardUiState(state, DashboardLocalState(), now, ZoneOffset.UTC) }

        assertEquals(PauseAvailability.AVAILABLE, ui(healthy).pauseAvailability)
        assertEquals(PauseAvailability.NO_PAUSES_LEFT, ui(healthy.copy(pausesToday = limit)).pauseAvailability)
        assertEquals(PauseAvailability.LEVEL_3_ACTIVE, ui(healthy.copy(isLevel3Active = true, pausesToday = limit)).pauseAvailability)
        assertEquals(limit - 1, ui(healthy.copy(pausesToday = 1)).pausesLeft)
    }

    @Test
    fun `the pause deadline is formatted as a wall-clock time`() {
        val eighteenForty = (18 * 60 + 40) * 60_000L
        val ui = buildDashboardUiState(healthy.copy(pausedUntilMillis = eighteenForty), DashboardLocalState(), now, ZoneOffset.UTC)

        assertEquals("18:40", ui.pausedUntilLabel)
        assertNull(buildDashboardUiState(healthy, DashboardLocalState(), now, ZoneOffset.UTC).pausedUntilLabel)
    }

    @Test
    fun `the thirty minute option is preselected`() {
        assertEquals(30, DashboardLocalState().selectedPauseMinutes)
        assert(DashboardLocalState().selectedPauseMinutes in EscalationConfig.PAUSE_OPTIONS_MIN)
    }
}
