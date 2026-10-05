package com.likkapet.domain

import com.likkapet.domain.model.TargetApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Watched apps added by the user (RF-S06) and the "at least one" rule (RF-S02). */
class WatchedAppsTest {
    private val tiktok = WatchedApps.packagesOf(TargetApp.TIKTOK)
    private val youtube = "com.google.android.youtube"
    private val chrome = "com.android.chrome"
    private val gmail = "com.google.android.gm"

    @Test
    fun `default packages resolve to their app and added ones to OTHER`() {
        val watched = EscalationConfig.DEFAULT_TARGET_PACKAGES.keys + chrome

        assertEquals(TargetApp.TIKTOK, WatchedApps.targetAppFor("com.zhiliaoapp.musically", watched))
        assertEquals(TargetApp.TIKTOK, WatchedApps.targetAppFor("com.ss.android.ugc.trill", watched))
        assertEquals(TargetApp.FACEBOOK, WatchedApps.targetAppFor("com.facebook.lite", watched))
        assertEquals(TargetApp.OTHER, WatchedApps.targetAppFor(chrome, watched))
    }

    @Test
    fun `an unwatched package is not a target, default or not`() {
        assertNull(WatchedApps.targetAppFor(youtube, watchedPackages = setOf(chrome)))
        assertNull(WatchedApps.targetAppFor(gmail, watchedPackages = setOf(chrome)))
    }

    @Test
    fun `OTHER is not one of the four default apps`() {
        assertEquals(listOf(TargetApp.TIKTOK, TargetApp.INSTAGRAM, TargetApp.YOUTUBE, TargetApp.FACEBOOK), TargetApp.DEFAULTS)
        assertTrue(WatchedApps.packagesOf(TargetApp.OTHER).isEmpty())
    }

    @Test
    fun `a default app with two packages counts once`() {
        assertEquals(1, WatchedApps.activeInstalledCount(tiktok, installedPackages = tiktok))
    }

    @Test
    fun `the only installed watched app cannot be switched off`() {
        val watched = setOf(youtube)

        assertNull(WatchedApps.toggle(setOf(youtube), watched = false, watchedPackages = watched, installedPackages = setOf(youtube)))
    }

    @Test
    fun `with a default and an added app, the default can go but then the added one cannot`() {
        val installed = setOf(youtube, chrome)
        val afterYoutube = WatchedApps.toggle(setOf(youtube), false, setOf(youtube, chrome), installed)

        assertEquals(setOf(chrome), afterYoutube)
        assertFalse(WatchedApps.canStopWatching(setOf(chrome), afterYoutube!!, installed))
    }

    @Test
    fun `a watched app that was uninstalled does not count`() {
        // Chrome is still watched but gone from the phone: YouTube is the last one really watched.
        val watched = setOf(youtube, chrome)
        val installed = setOf(youtube)

        assertEquals(1, WatchedApps.activeInstalledCount(watched, installed))
        assertFalse(WatchedApps.canStopWatching(setOf(youtube), watched, installed))
        assertTrue(WatchedApps.canStopWatching(setOf(chrome), watched, installed))
    }

    @Test
    fun `switching an app on is always allowed`() {
        assertEquals(
            setOf(chrome, gmail),
            WatchedApps.toggle(setOf(gmail), watched = true, watchedPackages = setOf(chrome), installedPackages = emptySet()),
        )
    }

    @Test
    fun `a default app is watched while any of its packages is`() {
        assertTrue(WatchedApps.isWatched(TargetApp.TIKTOK, setOf("com.ss.android.ugc.trill")))
        assertFalse(WatchedApps.isWatched(TargetApp.TIKTOK, setOf(chrome)))
    }
}
