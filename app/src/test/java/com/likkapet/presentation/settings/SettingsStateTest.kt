package com.likkapet.presentation.settings

import com.likkapet.domain.WatchedApps
import com.likkapet.domain.model.InstalledApp
import com.likkapet.domain.model.LikkaSettings
import com.likkapet.domain.model.TargetApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStateTest {
    private val youtube = InstalledApp("com.google.android.youtube", "YouTube")
    private val tiktok = InstalledApp("com.zhiliaoapp.musically", "TikTok")
    private val chrome = InstalledApp("com.android.chrome", "Chrome")
    private val notes = InstalledApp("org.example.notes", "Álbum de notas")
    private val installed = listOf(youtube, tiktok, chrome, notes)

    private fun ui(
        watched: Set<String>,
        added: Set<String> = emptySet(),
        launcherApps: List<InstalledApp>? = installed,
    ) = buildSettingsUiState(
        LikkaSettings(watchedPackages = watched, addedPackages = added),
        launcherApps,
        isDeactivateDialogVisible = false,
    )

    private fun packagesOf(app: TargetApp) = WatchedApps.packagesOf(app)

    @Test
    fun `every watched app can be switched off while others remain`() {
        val state = ui(packagesOf(TargetApp.TIKTOK) + youtube.packageName)

        assertTrue(state.watchedApps.filter { it.isWatched }.all { it.canToggle })
    }

    @Test
    fun `the last installed watched app is locked on`() {
        val tiktokUi = ui(packagesOf(TargetApp.TIKTOK)).watchedApps.first { it.app == TargetApp.TIKTOK }

        assertTrue(tiktokUi.isWatched)
        assertFalse(tiktokUi.canToggle)
    }

    @Test
    fun `an unwatched app can always be switched on`() {
        val instagram = ui(packagesOf(TargetApp.TIKTOK)).watchedApps.first { it.app == TargetApp.INSTAGRAM }

        assertFalse(instagram.isWatched)
        assertTrue(instagram.canToggle)
    }

    @Test
    fun `the four default apps are listed in order, never OTHER`() {
        assertEquals(TargetApp.DEFAULTS, ui(emptySet()).watchedApps.map { it.app })
    }

    @Test
    fun `added apps are listed alphabetically with their launcher label`() {
        val state = ui(watched = setOf(chrome.packageName, notes.packageName), added = setOf(chrome.packageName, notes.packageName))

        assertEquals(listOf("Álbum de notas", "Chrome"), state.addedApps.map { it.label })
    }

    @Test
    fun `with TikTok and an added app on, TikTok can go but then the added one is locked`() {
        val both = ui(watched = packagesOf(TargetApp.TIKTOK) + chrome.packageName, added = setOf(chrome.packageName))
        assertTrue(both.watchedApps.first { it.app == TargetApp.TIKTOK }.canToggle)

        val onlyAdded = ui(watched = setOf(chrome.packageName), added = setOf(chrome.packageName)).addedApps.single()
        assertFalse(onlyAdded.canToggle)
        assertFalse(onlyAdded.canRemove)
    }

    @Test
    fun `an added app that was uninstalled is hidden`() {
        val state = ui(watched = setOf(youtube.packageName, "gone.app"), added = setOf("gone.app"))

        assertTrue(state.addedApps.isEmpty())
    }

    @Test
    fun `nothing can be switched off while the installed apps are still loading`() {
        val state = ui(watched = packagesOf(TargetApp.TIKTOK) + youtube.packageName, launcherApps = null)

        assertTrue(state.watchedApps.filter { it.isWatched }.none { it.canToggle })
    }
}
