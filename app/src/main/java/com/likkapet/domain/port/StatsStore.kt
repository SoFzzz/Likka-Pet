package com.likkapet.domain.port

import com.likkapet.domain.model.LikkaSnapshot
import com.likkapet.domain.model.MiuiTask
import com.likkapet.domain.model.PauseResult
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Persistent state of RF-D01 (DataStore in data/). Reads are already rolled over to the current
 * day, so a new `today_date` shows zeroed counters even before anything is written (RF-D01); every
 * write persists that rollover first.
 */
interface StatsStore {
    val snapshot: Flow<LikkaSnapshot>

    /** Persists the daily rollover and the streak (RF-D04) without changing anything else. */
    suspend fun refreshDay()

    /** Ends onboarding and switches Likka on. */
    suspend fun completeOnboarding()

    /** Switching off also cancels a running pause and marks the day as not counting for the streak. */
    suspend fun setLikkaEnabled(enabled: Boolean)

    suspend fun setVibrationEnabled(enabled: Boolean)

    suspend fun setAiEnabled(enabled: Boolean)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setMiuiConfirmed(
        task: MiuiTask,
        confirmed: Boolean,
    )

    /**
     * Toggles one of the four default apps (all its packages). Returns false, changing nothing,
     * when it would leave no watched app among [installedPackages] (RF-S02).
     */
    suspend fun setDefaultAppWatched(
        app: TargetApp,
        watched: Boolean,
        installedPackages: Set<String>,
    ): Boolean

    /** Same as [setDefaultAppWatched] for an app the user added (RF-S06). */
    suspend fun setAddedAppWatched(
        packageName: String,
        watched: Boolean,
        installedPackages: Set<String>,
    ): Boolean

    /** Adds an app to `added_apps` and, switched on, to `watched_apps`. Default packages are ignored. */
    suspend fun addApp(packageName: String)

    /** Removes an added app from both sets; false when it is the last watched app installed (RF-S02). */
    suspend fun removeApp(
        packageName: String,
        installedPackages: Set<String>,
    ): Boolean

    /** Rule E (RF-S01); counts the pause and sets `paused_until` only when accepted. */
    suspend fun requestPause(
        minutes: Int,
        isLevel3Active: Boolean,
    ): PauseResult

    /** Ends the pause early; the pause still counts for the day. */
    suspend fun resume()

    /** Adds already-whole minutes to `usage_minutes_today` (RF-D02 batches them first). */
    suspend fun addUsageMinutes(minutes: Int)

    suspend fun recordIntervention()

    /** A level 3 today also breaks the streak (RF-D04). */
    suspend fun recordLevel3()
}
