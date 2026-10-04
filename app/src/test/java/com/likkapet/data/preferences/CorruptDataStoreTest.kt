package com.likkapet.data.preferences

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.PreferencesSerializer
import com.likkapet.domain.model.LikkaSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.ZoneOffset

/**
 * Documentación §6 Módulo 5, "Archivo dañado": a corrupt DataStore file starts again from the
 * defaults. Nothing here touches the disk: the invalid bytes go straight into the Preferences
 * serializer, and the handler that [LikkaDataStore] installs is called with the error it raises.
 */
class CorruptDataStoreTest {
    // A protobuf tag with a wire type that does not exist, then garbage: what a file cut in half can look like.
    private val invalidBytes = byteArrayOf(0x0F, 0x7F, 0x00, 0x13, 0x37, 0x42, 0x00, 0x00)

    private fun corruptionFromInvalidBytes(): CorruptionException =
        try {
            runBlocking { PreferencesSerializer.readFrom(Buffer().write(invalidBytes)) }
            fail("The serializer accepted invalid bytes")
            error("unreachable")
        } catch (e: CorruptionException) {
            e
        }

    @Test
    fun `the Preferences serializer rejects invalid bytes as corruption`() {
        assertTrue(corruptionFromInvalidBytes().message?.isNotEmpty() == true)
    }

    @Test
    fun `the corruption handler of LikkaDataStore returns empty preferences`() {
        val recovered = runBlocking { LikkaDataStore.corruptionHandler.handleCorruption(corruptionFromInvalidBytes()) }

        assertTrue(recovered.asMap().isEmpty())
    }

    @Test
    fun `a store that starts from the recovered preferences reads the documented defaults`() {
        val recovered = runBlocking { LikkaDataStore.corruptionHandler.handleCorruption(corruptionFromInvalidBytes()) }
        val dataStore = InMemoryPreferencesDataStore()
        runBlocking { dataStore.updateData { recovered } }
        val store = DataStoreStatsStore(dataStore, { 0L }) { ZoneOffset.UTC }

        val snapshot = runBlocking { store.snapshot.first() }

        assertEquals(LikkaSnapshot().settings, snapshot.settings)
        assertEquals(0, snapshot.today.usageMinutes)
        assertEquals(0, snapshot.today.streakDays)
    }
}
