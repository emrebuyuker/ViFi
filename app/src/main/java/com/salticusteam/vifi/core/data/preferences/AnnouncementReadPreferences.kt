package com.salticusteam.vifi.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Which announcements this device has opened (iOS keeps the same set in UserDefaults). Device-local only. */
interface AnnouncementReadPreferences {

    /** Ids of the announcements already read. */
    val readIds: Flow<Set<String>>

    suspend fun markRead(ids: Collection<String>)
}

@Singleton
class DataStoreAnnouncementReadPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AnnouncementReadPreferences {

    override val readIds: Flow<Set<String>> = dataStore.data.map { it[READ_IDS].orEmpty() }

    override suspend fun markRead(ids: Collection<String>) {
        dataStore.edit { prefs ->
            val current = prefs[READ_IDS].orEmpty()
            if (!current.containsAll(ids)) prefs[READ_IDS] = current + ids
        }
    }

    private companion object {
        val READ_IDS = stringSetPreferencesKey("announcements.readIds")
    }
}
