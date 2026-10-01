package com.likkapet.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile

/** The app's single Preferences DataStore; create it once (LikkaApplication), never per screen. */
object LikkaDataStore {
    private const val FILE_NAME = "likka_stats"

    fun create(context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            // A truncated file (process killed mid-write) would otherwise crash every launch:
            // start again from the defaults instead, as on a fresh install.
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
        )
}
