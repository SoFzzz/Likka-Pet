package com.likkapet.presentation.state

import com.likkapet.domain.model.PauseResult
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory stand-in for the DataStore-backed `StatsStore` (a later `data/` task). ViewModels talk
 * to this class only, so replacing it does not touch any screen. The rules it applies (last
 * watched app cannot be switched off, pause limits) mirror RF-S01/RF-S02 only as far as the fake
 * UI needs; the real ones live in `domain/`.
 */
class FakeAppStateStore(
    initial: FakeAppState,
) {
    private val mutableState = MutableStateFlow(initial)
    val state: StateFlow<FakeAppState> = mutableState.asStateFlow()

    fun update(transform: (FakeAppState) -> FakeAppState) = mutableState.update(transform)

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setVibrationEnabled(enabled: Boolean) = update { it.copy(vibrationEnabled = enabled) }

    fun setAiEnabled(enabled: Boolean) = update { it.copy(aiEnabled = enabled) }

    fun setLikkaEnabled(enabled: Boolean) =
        update { it.copy(likkaEnabled = enabled, pausedUntilMillis = if (enabled) it.pausedUntilMillis else null) }

    fun completeOnboarding() = update { it.copy(onboardingCompleted = true, likkaEnabled = true) }

    /** Ignored when it would leave no watched app (RF-S02). */
    fun setWatched(
        app: TargetApp,
        watched: Boolean,
    ) = update { current ->
        val next = if (watched) current.watchedApps + app else current.watchedApps - app
        if (next.isEmpty()) current else current.copy(watchedApps = next)
    }

    /** Stands in for the system permission screens, which this task does not open. */
    fun grantPermission(permission: AppPermission) =
        update { it.copy(permissions = it.permissions + (permission to PermissionStatus.GRANTED)) }

    fun setMiuiConfirmed(
        task: MiuiTask,
        confirmed: Boolean,
    ) = update { it.copy(miuiConfirmed = if (confirmed) it.miuiConfirmed + task else it.miuiConfirmed - task) }

    /** Applies rule E as the fake UI sees it; the pause count grows only when accepted. */
    fun requestPause(
        minutes: Int,
        nowMillis: Long,
    ): PauseResult {
        val current = state.value
        val result =
            when {
                current.isPausedAt(nowMillis) -> PauseResult.REJECTED_ALREADY_PAUSED
                current.isLevel3Active -> PauseResult.REJECTED_LEVEL_3
                current.pausesLeft == 0 -> PauseResult.REJECTED_DAILY_LIMIT
                else -> PauseResult.ACCEPTED
            }
        if (result == PauseResult.ACCEPTED) {
            update {
                it.copy(
                    pausedUntilMillis = nowMillis + minutes * MILLIS_PER_MINUTE,
                    pausesToday = it.pausesToday + 1,
                )
            }
        }
        return result
    }

    fun resume() = update { it.copy(pausedUntilMillis = null) }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
