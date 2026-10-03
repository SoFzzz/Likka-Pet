package com.likkapet.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.likkapet.domain.DailyStatsRules
import com.likkapet.domain.PauseRules
import com.likkapet.domain.WatchedApps
import com.likkapet.domain.model.AppLanguage
import com.likkapet.domain.model.LikkaSettings
import com.likkapet.domain.model.LikkaSnapshot
import com.likkapet.domain.model.MiuiTask
import com.likkapet.domain.model.PauseResult
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.ThemeMode
import com.likkapet.domain.port.StatsStore
import com.likkapet.domain.port.WallClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * [StatsStore] over Preferences DataStore (RF-D01). Every write reads the current snapshot, rolls
 * it over to today (RF-D01, RF-D04), applies one change and writes the whole snapshot back inside a
 * single `edit`, so each change is atomic. Reads apply the same rollover without writing.
 */
class DataStoreStatsStore(
    private val dataStore: DataStore<Preferences>,
    private val wallClock: WallClock,
    // Read on every use, so a time-zone change while the process lives moves today_date too.
    private val zone: () -> ZoneId,
) : StatsStore {
    // An unreadable file reads as the defaults instead of crashing the screens (Android logs the cause).
    override val snapshot: Flow<LikkaSnapshot> =
        dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { rolledOver(PreferencesMapper.read(it)) }

    override suspend fun refreshDay() = update { it }

    // Likka was "off" earlier today only because onboarding was not done: the install day can count.
    override suspend fun completeOnboarding() =
        update {
            it.copy(
                settings = it.settings.copy(onboardingCompleted = true, likkaEnabled = true),
                today = it.today.copy(likkaDisabledToday = false),
            )
        }

    override suspend fun setLikkaEnabled(enabled: Boolean) =
        update { current ->
            if (enabled) {
                current.copy(settings = current.settings.copy(likkaEnabled = true))
            } else {
                current.copy(
                    settings = current.settings.copy(likkaEnabled = false, pausedUntilMillis = null),
                    today = current.today.copy(likkaDisabledToday = true),
                )
            }
        }

    override suspend fun setVibrationEnabled(enabled: Boolean) = updateSettings { it.copy(vibrationEnabled = enabled) }

    override suspend fun setAiEnabled(enabled: Boolean) = updateSettings { it.copy(aiEnabled = enabled) }

    override suspend fun setThemeMode(mode: ThemeMode) = updateSettings { it.copy(themeMode = mode) }

    override suspend fun setAppLanguage(language: AppLanguage) = updateSettings { it.copy(appLanguage = language) }

    override suspend fun setMiuiConfirmed(
        task: MiuiTask,
        confirmed: Boolean,
    ) = updateSettings { it.copy(miuiConfirmed = if (confirmed) it.miuiConfirmed + task else it.miuiConfirmed - task) }

    override suspend fun setDefaultAppWatched(
        app: TargetApp,
        watched: Boolean,
        installedPackages: Set<String>,
    ): Boolean = toggleWatched(WatchedApps.packagesOf(app), watched, installedPackages)

    override suspend fun setAddedAppWatched(
        packageName: String,
        watched: Boolean,
        installedPackages: Set<String>,
    ): Boolean = toggleWatched(setOf(packageName), watched, installedPackages)

    override suspend fun addApp(packageName: String) {
        if (WatchedApps.isDefaultPackage(packageName)) return
        updateSettings {
            it.copy(addedPackages = it.addedPackages + packageName, watchedPackages = it.watchedPackages + packageName)
        }
    }

    override suspend fun removeApp(
        packageName: String,
        installedPackages: Set<String>,
    ): Boolean {
        var isRemoved = false
        updateSettings { settings ->
            val canRemove = WatchedApps.canStopWatching(setOf(packageName), settings.watchedPackages, installedPackages)
            isRemoved = canRemove
            if (!canRemove) {
                settings
            } else {
                settings.copy(
                    addedPackages = settings.addedPackages - packageName,
                    watchedPackages = settings.watchedPackages - packageName,
                )
            }
        }
        return isRemoved
    }

    override suspend fun requestPause(
        minutes: Int,
        isLevel3Active: Boolean,
    ): PauseResult {
        var result = PauseResult.ACCEPTED
        update { current ->
            val now = wallClock.nowEpochMillis()
            val rejection =
                PauseRules.rejectionFor(
                    isPaused = current.isPausedAt(now),
                    // Ejection is only known by EscalationCoordinator, which decides while the service runs;
                    // this path is the service-off one, where no session can be ejected.
                    isEjected = false,
                    hasTrackAtLevel3 = isLevel3Active,
                    pausesUsedToday = current.today.pauses,
                )
            result = rejection ?: PauseResult.ACCEPTED
            if (rejection != null) current else startPause(current, now, minutes)
        }
        return result
    }

    override suspend fun startPause(minutes: Int) = update { startPause(it, wallClock.nowEpochMillis(), minutes) }

    override suspend fun resume() = updateSettings { it.copy(pausedUntilMillis = null) }

    override suspend fun addUsageMinutes(minutes: Int) =
        update {
            it.copy(
                today =
                    it.today.copy(
                        usageMinutes =
                            it.today.usageMinutes + minutes,
                    ),
            )
        }

    override suspend fun recordIntervention() = update { it.copy(today = it.today.copy(interventions = it.today.interventions + 1)) }

    override suspend fun recordLevel3() = update { it.copy(today = it.today.copy(level3Count = it.today.level3Count + 1)) }

    private fun startPause(
        current: LikkaSnapshot,
        now: Long,
        minutes: Int,
    ): LikkaSnapshot =
        current.copy(
            settings = current.settings.copy(pausedUntilMillis = now + TimeUnit.MINUTES.toMillis(minutes.toLong())),
            today = current.today.copy(pauses = current.today.pauses + 1),
        )

    private suspend fun toggleWatched(
        packages: Set<String>,
        watched: Boolean,
        installedPackages: Set<String>,
    ): Boolean {
        var isApplied = false
        updateSettings { settings ->
            val next = WatchedApps.toggle(packages, watched, settings.watchedPackages, installedPackages)
            isApplied = next != null
            if (next == null) settings else settings.copy(watchedPackages = next)
        }
        return isApplied
    }

    private suspend fun updateSettings(transform: (LikkaSettings) -> LikkaSettings) = update { it.copy(settings = transform(it.settings)) }

    private suspend fun update(transform: (LikkaSnapshot) -> LikkaSnapshot) {
        dataStore.edit { preferences ->
            PreferencesMapper.write(preferences, transform(rolledOver(PreferencesMapper.read(preferences))))
        }
    }

    private fun rolledOver(snapshot: LikkaSnapshot): LikkaSnapshot =
        snapshot.copy(today = DailyStatsRules.rollOver(snapshot.today, today(), snapshot.settings.likkaEnabled))

    private fun today(): LocalDate = Instant.ofEpochMilli(wallClock.nowEpochMillis()).atZone(zone()).toLocalDate()
}
