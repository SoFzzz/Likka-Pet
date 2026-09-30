package com.likkapet.presentation.state

import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.model.PauseResult
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAppStateStoreTest {
    private val now = 10_000_000L
    private val store = FakeAppStateStore(FakeAppState(likkaEnabled = true))

    @Test
    fun `the last watched app cannot be switched off`() {
        TargetApp.entries.drop(1).forEach { store.setWatched(it, watched = false) }
        assertEquals(setOf(TargetApp.TIKTOK), store.state.value.watchedApps)

        store.setWatched(TargetApp.TIKTOK, watched = false)

        assertEquals(setOf(TargetApp.TIKTOK), store.state.value.watchedApps)
    }

    @Test
    fun `a switched-off app can be watched again`() {
        store.setWatched(TargetApp.YOUTUBE, watched = false)
        store.setWatched(TargetApp.YOUTUBE, watched = true)

        assertEquals(TargetApp.entries.toSet(), store.state.value.watchedApps)
    }

    @Test
    fun `an accepted pause counts once and sets its deadline`() {
        assertEquals(PauseResult.ACCEPTED, store.requestPause(30, now))

        assertEquals(1, store.state.value.pausesToday)
        assertEquals(now + 30 * 60_000L, store.state.value.pausedUntilMillis)
    }

    @Test
    fun `a pause is rejected while another is running`() {
        store.requestPause(15, now)

        assertEquals(PauseResult.REJECTED_ALREADY_PAUSED, store.requestPause(15, now + 1))
        assertEquals(1, store.state.value.pausesToday)
    }

    @Test
    fun `the fourth pause of the day is rejected`() {
        repeat(EscalationConfig.MAX_PAUSES_PER_DAY) {
            assertEquals(PauseResult.ACCEPTED, store.requestPause(15, now))
            store.resume()
        }

        assertEquals(PauseResult.REJECTED_DAILY_LIMIT, store.requestPause(15, now))
        assertEquals(EscalationConfig.MAX_PAUSES_PER_DAY, store.state.value.pausesToday)
    }

    @Test
    fun `no pause is accepted during level 3`() {
        store.update { it.copy(isLevel3Active = true) }

        assertEquals(PauseResult.REJECTED_LEVEL_3, store.requestPause(15, now))
        assertEquals(0, store.state.value.pausesToday)
    }

    @Test
    fun `resuming clears the deadline without refunding the pause`() {
        store.requestPause(15, now)
        store.resume()

        assertNull(store.state.value.pausedUntilMillis)
        assertEquals(1, store.state.value.pausesToday)
    }

    @Test
    fun `switching Likka off cancels a running pause`() {
        store.requestPause(15, now)
        store.setLikkaEnabled(false)

        assertFalse(store.state.value.likkaEnabled)
        assertNull(store.state.value.pausedUntilMillis)
    }

    @Test
    fun `finishing onboarding enables Likka`() {
        store.update { it.copy(likkaEnabled = false) }
        store.completeOnboarding()

        assertTrue(store.state.value.onboardingCompleted)
        assertTrue(store.state.value.likkaEnabled)
    }

    @Test
    fun `theme defaults to dark and keeps the chosen mode`() {
        assertEquals(ThemeMode.DARK, store.state.value.themeMode)
        store.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, store.state.value.themeMode)
    }

    @Test
    fun `granting a permission only changes that permission`() {
        val pending = FakeAppStateStore(FakeAppState(permissions = pendingPermissions(includeNotifications = true)))
        pending.grantPermission(AppPermission.OVERLAY)

        assertEquals(PermissionStatus.GRANTED, pending.state.value.permissions[AppPermission.OVERLAY])
        assertEquals(PermissionStatus.PENDING, pending.state.value.permissions[AppPermission.USAGE_STATS])
        assertFalse(pending.state.value.hasAllPermissions)
    }

    @Test
    fun `notifications are part of the checklist only where the OS asks for them`() {
        assertEquals(3, pendingPermissions(includeNotifications = true).size)
        assertEquals(
            setOf(AppPermission.OVERLAY, AppPermission.USAGE_STATS),
            pendingPermissions(includeNotifications = false).keys,
        )
    }
}
