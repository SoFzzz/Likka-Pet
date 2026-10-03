package com.likkapet.data.preferences

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.likkapet.domain.model.AppLanguage
import com.likkapet.domain.model.DailyStats
import com.likkapet.domain.model.LikkaSettings
import com.likkapet.domain.model.LikkaSnapshot
import com.likkapet.domain.model.MiuiTask
import com.likkapet.domain.model.ThemeMode
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** DataStore keys of RF-D01 and their mapping to [LikkaSnapshot]; a missing key reads as its default. */
internal object PreferencesMapper {
    private val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
    private val likkaEnabled = booleanPreferencesKey("likka_enabled")
    private val vibrationEnabled = booleanPreferencesKey("vibration_enabled")
    private val aiEnabled = booleanPreferencesKey("ai_enabled")
    private val themeMode = stringPreferencesKey("theme_mode")
    private val appLanguage = stringPreferencesKey("app_language")
    private val watchedApps = stringSetPreferencesKey("watched_apps")
    private val addedApps = stringSetPreferencesKey("added_apps")
    private val pausedUntil = longPreferencesKey("paused_until")
    private val miuiKeys =
        mapOf(
            MiuiTask.AUTOSTART to booleanPreferencesKey("miui_autostart_confirmed"),
            MiuiTask.BATTERY to booleanPreferencesKey("miui_battery_confirmed"),
            MiuiTask.POPUP to booleanPreferencesKey("miui_popup_confirmed"),
        )

    private val todayDate = stringPreferencesKey("today_date")
    private val usageMinutesToday = intPreferencesKey("usage_minutes_today")
    private val interventionsToday = intPreferencesKey("interventions_today")
    private val level3CountToday = intPreferencesKey("level3_count_today")
    private val pausesToday = intPreferencesKey("pauses_today")

    // Not in the RF-D01 list yet (pending doc): whether Likka was off at any moment today (RF-D04).
    private val likkaDisabledToday = booleanPreferencesKey("likka_disabled_today")
    private val streakDays = intPreferencesKey("streak_days")
    private val lastStreakDate = stringPreferencesKey("last_streak_date")

    fun read(preferences: Preferences): LikkaSnapshot = LikkaSnapshot(readSettings(preferences), readStats(preferences))

    fun write(
        preferences: MutablePreferences,
        snapshot: LikkaSnapshot,
    ) {
        writeSettings(preferences, snapshot.settings)
        writeStats(preferences, snapshot.today)
    }

    private fun readSettings(preferences: Preferences): LikkaSettings {
        val defaults = LikkaSettings()
        return LikkaSettings(
            onboardingCompleted = preferences[onboardingCompleted] ?: defaults.onboardingCompleted,
            likkaEnabled = preferences[likkaEnabled] ?: defaults.likkaEnabled,
            vibrationEnabled = preferences[vibrationEnabled] ?: defaults.vibrationEnabled,
            aiEnabled = preferences[aiEnabled] ?: defaults.aiEnabled,
            themeMode = ThemeMode.entries.firstOrNull { it.name == preferences[themeMode] } ?: defaults.themeMode,
            appLanguage = AppLanguage.entries.firstOrNull { it.name == preferences[appLanguage] } ?: defaults.appLanguage,
            watchedPackages = preferences[watchedApps] ?: defaults.watchedPackages,
            addedPackages = preferences[addedApps] ?: defaults.addedPackages,
            miuiConfirmed = miuiKeys.filterValues { preferences[it] == true }.keys,
            pausedUntilMillis = preferences[pausedUntil],
        )
    }

    private fun readStats(preferences: Preferences): DailyStats =
        DailyStats(
            date = parseDate(preferences[todayDate]),
            usageMinutes = preferences[usageMinutesToday] ?: 0,
            interventions = preferences[interventionsToday] ?: 0,
            level3Count = preferences[level3CountToday] ?: 0,
            pauses = preferences[pausesToday] ?: 0,
            likkaDisabledToday = preferences[likkaDisabledToday] ?: false,
            streakDays = preferences[streakDays] ?: 0,
            lastStreakDate = parseDate(preferences[lastStreakDate]),
        )

    private fun writeSettings(
        preferences: MutablePreferences,
        settings: LikkaSettings,
    ) {
        preferences[onboardingCompleted] = settings.onboardingCompleted
        preferences[likkaEnabled] = settings.likkaEnabled
        preferences[vibrationEnabled] = settings.vibrationEnabled
        preferences[aiEnabled] = settings.aiEnabled
        preferences[themeMode] = settings.themeMode.name
        preferences[appLanguage] = settings.appLanguage.name
        preferences[watchedApps] = settings.watchedPackages
        preferences[addedApps] = settings.addedPackages
        miuiKeys.forEach { (task, key) -> preferences[key] = task in settings.miuiConfirmed }
        settings.pausedUntilMillis?.let { preferences[pausedUntil] = it } ?: preferences.remove(pausedUntil)
    }

    private fun writeStats(
        preferences: MutablePreferences,
        stats: DailyStats,
    ) {
        stats.date?.let { preferences[todayDate] = it.toString() } ?: preferences.remove(todayDate)
        preferences[usageMinutesToday] = stats.usageMinutes
        preferences[interventionsToday] = stats.interventions
        preferences[level3CountToday] = stats.level3Count
        preferences[pausesToday] = stats.pauses
        preferences[likkaDisabledToday] = stats.likkaDisabledToday
        preferences[streakDays] = stats.streakDays
        stats.lastStreakDate?.let { preferences[lastStreakDate] = it.toString() } ?: preferences.remove(lastStreakDate)
    }

    // ISO yyyy-MM-dd (RF-D01); an unreadable value counts as "no date", which starts a fresh day.
    private fun parseDate(value: String?): LocalDate? =
        value?.let {
            try {
                LocalDate.parse(it)
            } catch (e: DateTimeParseException) {
                null
            }
        }
}
