package com.salticusteam.vifi.core.data.inbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pre-4.1.0 per-device read ids of the announcements (`announcements.readIds`), replaced by the server's
 * `settings/inbox.readAt`. Only ever cleared now, once the first marker of an account was written (iOS drops
 * `announcements.readIds.v1` the same way); kept if that write fails.
 */
interface LegacyInboxReadState {
    suspend fun clear()
}

@Singleton
class DataStoreLegacyInboxReadState @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : LegacyInboxReadState {

    override suspend fun clear() {
        dataStore.edit { it.remove(READ_IDS) }
    }

    private companion object {
        val READ_IDS = stringSetPreferencesKey("announcements.readIds")
    }
}
