package com.likkapet.presentation.settings

import com.likkapet.domain.WatchedApps
import com.likkapet.domain.model.AppLanguage
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
    private val instagram = InstalledApp("com.instagram.android", "Instagram")
    private val facebookLite = InstalledApp("com.facebook.lite", "Facebook Lite")
    private val chrome = InstalledApp("com.android.chrome", "Chrome")
    private val notes = InstalledApp("org.example.notes", "Álbum de notas")
    private val installed = listOf(youtube, tiktok, instagram, facebookLite, chrome, notes)

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
        val instagramUi = ui(packagesOf(TargetApp.TIKTOK)).watchedApps.first { it.app == TargetApp.INSTAGRAM }

        assertFalse(instagramUi.isWatched)
        assertTrue(instagramUi.canToggle)
    }

    @Test
    fun `installed default apps are listed in order, never OTHER`() {
        assertEquals(TargetApp.DEFAULTS, ui(emptySet()).watchedApps.map { it.app })
    }

    @Test
    fun `a default app that is not installed is not listed`() {
        val state = ui(EscalationDefaults.watched, launcherApps = listOf(youtube, chrome))

        assertEquals(listOf(TargetApp.YOUTUBE), state.watchedApps.map { it.app })
    }

    @Test
    fun `a default app shows up with its stored value once installed`() {
        // Instagram was switched off earlier; installing it later shows it off, not reset.
        val watched = EscalationDefaults.watched - instagram.packageName

        val before = ui(watched, launcherApps = listOf(youtube))
        val after = ui(watched, launcherApps = listOf(youtube, instagram)).watchedApps.first { it.app == TargetApp.INSTAGRAM }

        assertFalse(before.watchedApps.any { it.app == TargetApp.INSTAGRAM })
        assertFalse(after.isWatched)
    }

    @Test
    fun `any one installed package is enough to list a two-package app`() {
        val state = ui(EscalationDefaults.watched, launcherApps = listOf(facebookLite))

        assertEquals(listOf(TargetApp.FACEBOOK), state.watchedApps.map { it.app })
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
    fun `no rows and no empty notice while the installed apps are still loading`() {
        val state = ui(watched = packagesOf(TargetApp.TIKTOK) + chrome.packageName, added = setOf(chrome.packageName), launcherApps = null)

        assertTrue(state.isLoadingApps)
        assertTrue(state.watchedApps.isEmpty())
        assertTrue(state.addedApps.isEmpty())
        assertFalse(state.hasNoVisibleApps)
    }

    @Test
    fun `with no watched app installed the list is empty and shows the notice`() {
        val state = ui(EscalationDefaults.watched, added = setOf("gone.app"), launcherApps = listOf(chrome))

        assertTrue(state.watchedApps.isEmpty())
        assertTrue(state.addedApps.isEmpty())
        assertTrue(state.hasNoVisibleApps)
    }

    @Test
    fun `the notice does not show while any app is listed`() {
        assertFalse(ui(EscalationDefaults.watched).hasNoVisibleApps)
    }

    @Test
    fun `language is mapped from settings`() {
        val state =
            buildSettingsUiState(
                LikkaSettings(appLanguage = AppLanguage.EN),
                installed,
                isDeactivateDialogVisible = false,
            )
        assertEquals(AppLanguage.EN, state.appLanguage)
    }

    private object EscalationDefaults {
        val watched: Set<String> = LikkaSettings().watchedPackages
    }
}
