package com.likkapet.domain

import com.likkapet.domain.model.InstalledApp
import java.text.Normalizer
import java.util.Locale

/** Order and search of the "Añadir app" list (design system §2.5): case- and accent-insensitive. */
object AppSearch {
    private val combiningMarks = Regex("\\p{M}+")

    /** Launcher apps that can be added: not one of the default apps, sorted by name. */
    fun addableApps(launcherApps: List<InstalledApp>): List<InstalledApp> =
        sortedByLabel(launcherApps.filterNot { WatchedApps.isDefaultPackage(it.packageName) }.distinctBy { it.packageName })

    fun sortedByLabel(apps: List<InstalledApp>): List<InstalledApp> =
        apps.sortedWith(compareBy<InstalledApp> { normalize(it.label) }.thenBy { it.packageName })

    fun filter(
        apps: List<InstalledApp>,
        query: String,
    ): List<InstalledApp> {
        val needle = normalize(query.trim())
        if (needle.isEmpty()) return apps
        return apps.filter { normalize(it.label).contains(needle) }
    }

    fun normalize(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
}
