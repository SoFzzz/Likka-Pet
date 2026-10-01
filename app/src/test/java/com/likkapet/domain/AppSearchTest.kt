package com.likkapet.domain

import com.likkapet.domain.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

/** "Añadir app" list: no default apps, alphabetical, case- and accent-insensitive search (design system §2.5). */
class AppSearchTest {
    private val apps =
        listOf(
            InstalledApp("com.spotify.music", "Spotify"),
            InstalledApp("com.google.android.youtube", "YouTube"),
            InstalledApp("org.example.camera", "Cámara"),
            InstalledApp("com.android.chrome", "chrome"),
            InstalledApp("com.instagram.android", "Instagram"),
        )

    @Test
    fun `default apps are never offered`() {
        val packages = AppSearch.addableApps(apps).map { it.packageName }

        assertEquals(listOf("org.example.camera", "com.android.chrome", "com.spotify.music"), packages)
    }

    @Test
    fun `sorting ignores case and accents`() {
        assertEquals(listOf("Cámara", "chrome", "Spotify"), AppSearch.addableApps(apps).map { it.label })
    }

    @Test
    fun `search matches part of the name without case or accents`() {
        val addable = AppSearch.addableApps(apps)

        assertEquals(listOf("Cámara"), AppSearch.filter(addable, "CAMA").map { it.label })
        assertEquals(listOf("Spotify"), AppSearch.filter(addable, "tif").map { it.label })
        assertEquals(addable, AppSearch.filter(addable, "  "))
        assertEquals(emptyList<InstalledApp>(), AppSearch.filter(addable, "netflix"))
    }
}
