package com.likkapet.data.preferences

import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.model.LikkaSnapshot
import com.likkapet.domain.model.MiuiTask
import com.likkapet.domain.model.PauseResult
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.ThemeMode
import com.likkapet.domain.port.WallClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** RF-D01, RF-D04, RF-S01, RF-S02, RF-S06 and RF-S08 over Preferences with a virtual wall clock. */
class DataStoreStatsStoreTest {
    private val day1 = LocalDate.of(2026, 10, 5)
    private var nowMs = epochMillisAt(day1, hour = 10)
    private val clock = WallClock { nowMs }
    private val dataStore = InMemoryPreferencesDataStore()
    private var store = openStore()

    private val chrome = "com.android.chrome"
    private val youtube = "com.google.android.youtube"
    private val installed = setOf(chrome, youtube)

    // A new store over the same preferences, as after a process restart.
    private fun openStore() = DataStoreStatsStore(dataStore, clock) { ZoneOffset.UTC }

    private fun snapshot(): LikkaSnapshot = runBlocking { store.snapshot.first() }

    private fun moveTo(
        day: LocalDate,
        hour: Int = 10,
    ) {
        nowMs = epochMillisAt(day, hour)
    }

    @Test
    fun `a fresh install reads the documented defaults`() {
        val settings = snapshot().settings

        assertFalse(settings.onboardingCompleted)
        assertFalse(settings.likkaEnabled)
        assertTrue(settings.aiEnabled)
        assertTrue(settings.vibrationEnabled)
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(EscalationConfig.DEFAULT_TARGET_PACKAGES.keys, settings.watchedPackages)
        assertTrue(settings.addedPackages.isEmpty())
    }

    @Test
    fun `settings survive reopening the store`() {
        runBlocking {
            store.completeOnboarding()
            store.setThemeMode(ThemeMode.LIGHT)
            store.setAiEnabled(false)
            store.setMiuiConfirmed(MiuiTask.BATTERY, confirmed = true)
            store.addApp(chrome)
        }
        store = openStore()

        val settings = snapshot().settings
        assertTrue(settings.onboardingCompleted)
        assertTrue(settings.likkaEnabled)
        assertEquals(ThemeMode.LIGHT, settings.themeMode)
        assertFalse(settings.aiEnabled)
        assertEquals(setOf(MiuiTask.BATTERY), settings.miuiConfirmed)
        assertEquals(setOf(chrome), settings.addedPackages)
    }

    @Test
    fun `a date change zeroes the counters when read, before any write`() {
        runBlocking {
            store.addUsageMinutes(25)
            store.recordIntervention()
            store.recordLevel3()
            store.requestPause(15, isLevel3Active = false)
        }
        moveTo(day1.plusDays(1))

        val today = snapshot().today
        assertEquals(day1.plusDays(1), today.date)
        assertEquals(listOf(0, 0, 0, 0), listOf(today.usageMinutes, today.interventions, today.level3Count, today.pauses))
    }

    @Test
    fun `a date change keeps the theme and the watched and added apps`() {
        runBlocking {
            store.setThemeMode(ThemeMode.SYSTEM)
            store.addApp(chrome)
            store.setDefaultAppWatched(TargetApp.INSTAGRAM, watched = false, installedPackages = installed)
        }
        moveTo(day1.plusDays(1))
        runBlocking { store.refreshDay() }

        val settings = snapshot().settings
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(setOf(chrome), settings.addedPackages)
        assertTrue(chrome in settings.watchedPackages)
        assertFalse("com.instagram.android" in settings.watchedPackages)
    }

    @Test
    fun `a day without level 3 adds to the streak and a day with it resets it`() {
        runBlocking { store.completeOnboarding() }
        moveTo(day1.plusDays(1))
        runBlocking {
            store.refreshDay()
            store.requestPause(15, isLevel3Active = false)
        }
        assertEquals(1, snapshot().today.streakDays)

        moveTo(day1.plusDays(2))
        runBlocking { store.recordLevel3() }
        assertEquals(2, snapshot().today.streakDays)

        moveTo(day1.plusDays(3))
        assertEquals(0, snapshot().today.streakDays)
    }

    @Test
    fun `switching Likka off breaks the day and cancels the pause`() {
        runBlocking {
            store.completeOnboarding()
            store.requestPause(30, isLevel3Active = false)
            store.setLikkaEnabled(false)
            store.setLikkaEnabled(true)
        }
        assertNull(snapshot().settings.pausedUntilMillis)
        assertTrue(snapshot().today.likkaDisabledToday)

        moveTo(day1.plusDays(1))
        assertEquals(0, snapshot().today.streakDays)
    }

    @Test
    fun `an accepted pause counts once and sets its deadline`() {
        val result = runBlocking { store.requestPause(30, isLevel3Active = false) }

        assertEquals(PauseResult.ACCEPTED, result)
        assertEquals(1, snapshot().today.pauses)
        assertEquals(nowMs + 30 * 60_000L, snapshot().settings.pausedUntilMillis)
        assertTrue(snapshot().isPausedAt(nowMs))
    }

    @Test
    fun `the fourth pause is rejected and a running pause blocks another`() {
        runBlocking {
            assertEquals(PauseResult.ACCEPTED, store.requestPause(15, isLevel3Active = false))
            assertEquals(PauseResult.REJECTED_ALREADY_PAUSED, store.requestPause(15, isLevel3Active = false))
            store.resume()
            repeat(EscalationConfig.MAX_PAUSES_PER_DAY - 1) {
                assertEquals(PauseResult.ACCEPTED, store.requestPause(15, isLevel3Active = false))
                store.resume()
            }
            assertEquals(PauseResult.REJECTED_DAILY_LIMIT, store.requestPause(15, isLevel3Active = false))
        }
        assertEquals(EscalationConfig.MAX_PAUSES_PER_DAY, snapshot().today.pauses)
    }

    @Test
    fun `a pause is rejected during level 3 and not counted`() {
        assertEquals(PauseResult.REJECTED_LEVEL_3, runBlocking { store.requestPause(15, isLevel3Active = true) })
        assertEquals(0, snapshot().today.pauses)
    }

    @Test
    fun `a pause is over once its deadline passes`() {
        runBlocking { store.requestPause(15, isLevel3Active = false) }
        nowMs += 15 * 60_000L

        assertFalse(snapshot().isPausedAt(nowMs))
    }

    @Test
    fun `switching off the last installed watched app is rejected, even an added one`() {
        runBlocking {
            store.addApp(chrome)
            TargetApp.DEFAULTS.forEach { store.setDefaultAppWatched(it, watched = false, installedPackages = installed) }
            assertFalse(store.setAddedAppWatched(chrome, watched = false, installedPackages = installed))
            assertFalse(store.removeApp(chrome, installed))
        }
        assertEquals(setOf(chrome), snapshot().settings.watchedPackages)
    }

    @Test
    fun `removing an added app takes it out of both sets`() {
        runBlocking {
            store.addApp(chrome)
            assertTrue(store.removeApp(chrome, installed))
        }
        assertFalse(chrome in snapshot().settings.addedPackages)
        assertFalse(chrome in snapshot().settings.watchedPackages)
    }

    @Test
    fun `a default package cannot be added as another app`() {
        runBlocking { store.addApp(youtube) }

        assertTrue(snapshot().settings.addedPackages.isEmpty())
    }

    @Test
    fun `usage minutes, interventions and level 3 add up for the day`() {
        runBlocking {
            store.addUsageMinutes(2)
            store.addUsageMinutes(3)
            store.recordIntervention()
            store.recordLevel3()
        }
        val today = snapshot().today
        assertEquals(listOf(5, 1, 1), listOf(today.usageMinutes, today.interventions, today.level3Count))
    }

    private fun epochMillisAt(
        day: LocalDate,
        hour: Int,
    ): Long = day.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
}
