package com.likkapet.presentation.settings

import com.likkapet.domain.model.TargetApp
import com.likkapet.presentation.state.FakeAppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStateTest {
    private fun ui(watched: Set<TargetApp>) = buildSettingsUiState(FakeAppState(watchedApps = watched), isDeactivateDialogVisible = false)

    @Test
    fun `every watched app can be switched off while others remain`() {
        assertTrue(ui(TargetApp.entries.toSet()).watchedApps.all { it.canToggle })
    }

    @Test
    fun `the last watched app is locked on`() {
        val apps = ui(setOf(TargetApp.TIKTOK)).watchedApps

        val tiktok = apps.first { it.app == TargetApp.TIKTOK }
        assertTrue(tiktok.isWatched)
        assertFalse(tiktok.canToggle)
    }

    @Test
    fun `an unwatched app can always be switched on`() {
        val instagram = ui(setOf(TargetApp.TIKTOK)).watchedApps.first { it.app == TargetApp.INSTAGRAM }

        assertFalse(instagram.isWatched)
        assertTrue(instagram.canToggle)
    }

    @Test
    fun `all four watched apps are listed in enum order`() {
        assertEquals(TargetApp.entries, ui(TargetApp.entries.toSet()).watchedApps.map { it.app })
    }
}
