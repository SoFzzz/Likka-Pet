package com.likkapet.domain

import com.likkapet.domain.model.TargetApp

/**
 * Watched-app rules (RF-A02, RF-S02, RF-S06) over package-name sets. A "watched app" is one of the
 * four default apps (all its packages count as one) or one added package. Only apps still installed
 * count for the "at least one watched app" rule, so the user cannot end up watching only apps that
 * are gone.
 */
object WatchedApps {
    private val defaults = EscalationConfig.DEFAULT_TARGET_PACKAGES

    /** What the coordinator sees for a foreground package: its default app, [TargetApp.OTHER] if added, else null. */
    fun targetAppFor(
        packageName: String,
        watchedPackages: Set<String>,
    ): TargetApp? {
        if (packageName !in watchedPackages) return null
        return defaults[packageName] ?: TargetApp.OTHER
    }

    fun isDefaultPackage(packageName: String): Boolean = packageName in defaults

    fun packagesOf(app: TargetApp): Set<String> = defaults.filterValues { it == app }.keys

    fun isWatched(
        app: TargetApp,
        watchedPackages: Set<String>,
    ): Boolean = packagesOf(app).any { it in watchedPackages }

    /** How many watched apps are still installed, each default app counted once. */
    fun activeInstalledCount(
        watchedPackages: Set<String>,
        installedPackages: Set<String>,
    ): Int = (watchedPackages intersect installedPackages).map { defaults[it]?.name ?: it }.toSet().size

    /** Whether [packages] can stop being watched without leaving no installed watched app. */
    fun canStopWatching(
        packages: Set<String>,
        watchedPackages: Set<String>,
        installedPackages: Set<String>,
    ): Boolean = activeInstalledCount(watchedPackages - packages, installedPackages) > 0

    /** The new watched set, or null when switching [packages] off is not allowed. */
    fun toggle(
        packages: Set<String>,
        watched: Boolean,
        watchedPackages: Set<String>,
        installedPackages: Set<String>,
    ): Set<String>? =
        when {
            watched -> watchedPackages + packages
            canStopWatching(packages, watchedPackages, installedPackages) -> watchedPackages - packages
            else -> null
        }
}
