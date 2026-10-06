package com.likkapet.presentation.settings

import com.likkapet.domain.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The loading / list / no-results states of "Añadir app" (design system §2.5). */
class AddAppStateTest {
    private val chrome = InstalledApp("com.android.chrome", "Chrome")
    private val gmail = InstalledApp("com.google.android.gm", "Gmail")
    private val apps = listOf(chrome, gmail)

    @Test
    fun `apps still being read show the loading state`() {
        assertEquals(AddAppListState.Loading, buildAddAppUiState(null, emptySet(), "").list)
    }

    @Test
    fun `an added app is marked as added and stays in the list`() {
        val list = buildAddAppUiState(apps, setOf(chrome.packageName), "").list as AddAppListState.Results

        assertEquals(listOf(true, false), list.apps.map { it.isAdded })
        assertFalse(list.isEverythingAdded)
    }

    @Test
    fun `a search with no match shows the no-results state`() {
        assertEquals(AddAppListState.NoResults, buildAddAppUiState(apps, emptySet(), "netflix").list)
    }

    @Test
    fun `a search filters the list`() {
        val list = buildAddAppUiState(apps, emptySet(), "gm").list as AddAppListState.Results

        assertEquals(listOf("Gmail"), list.apps.map { it.label })
    }

    @Test
    fun `with every app added the screen says so`() {
        val all = buildAddAppUiState(apps, apps.map { it.packageName }.toSet(), "").list as AddAppListState.Results
        val none = buildAddAppUiState(emptyList(), emptySet(), "").list as AddAppListState.Results

        assertTrue(all.isEverythingAdded)
        assertTrue(none.isEverythingAdded)
    }
}
