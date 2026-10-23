package com.salticusteam.vifi.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    override val skippedUpdateVersion: Flow<String?> = dataStore.data.map { it[SKIPPED_UPDATE_VERSION] }

    override val notificationsPrompted: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_PROMPTED] ?: false }

    override val prefetchDone: Flow<Boolean> = dataStore.data.map { it[PREFETCH_DONE] ?: false }

    override suspend fun setSkippedUpdateVersion(version: String?) {
        dataStore.edit { if (version == null) it.remove(SKIPPED_UPDATE_VERSION) else it[SKIPPED_UPDATE_VERSION] = version }
    }

    override suspend fun setNotificationsPrompted(prompted: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_PROMPTED] = prompted }
    }

    override suspend fun setPrefetchDone(done: Boolean) {
        dataStore.edit { it[PREFETCH_DONE] = done }
    }

    private companion object {
        val SKIPPED_UPDATE_VERSION = stringPreferencesKey("update.skippedVersion")
        val NOTIFICATIONS_PROMPTED = booleanPreferencesKey("notifications.prompted")
        val PREFETCH_DONE = booleanPreferencesKey("prefetch.done")
    }
}
