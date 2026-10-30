package com.salticusteam.vifi.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * This install's identity for push registration (iOS `DeviceInstallationID`): the id of its
 * `users/<uid>/devices/<id>` document, stable for the life of the install so a token refresh updates the same
 * document (a fresh install gets a new one; the old document dies with its token). Kept on the device only, through
 * sign-outs; not a secret.
 */
@Singleton
class DeviceInstallation @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    // Internal state
    private val mutex = Mutex()

    /** The per-install id, a lowercase UUID created on first use; a stored value outside the rules' pattern is replaced. */
    suspend fun id(): String = mutex.withLock {
        val stored = dataStore.data.first()[INSTALLATION_ID]
        if (stored != null && isValidId(stored)) return stored
        val fresh = UUID.randomUUID().toString().lowercase()
        dataStore.edit { it[INSTALLATION_ID] = fresh }
        fresh
    }

    /** Whether the pre-4.1.0 `devices/<token>` document of this install was already removed. */
    suspend fun isLegacyDeviceRemoved(): Boolean = dataStore.data.first()[LEGACY_DEVICE_REMOVED] == true

    suspend fun markLegacyDeviceRemoved() {
        dataStore.edit { it[LEGACY_DEVICE_REMOVED] = true }
    }

    companion object {
        /** The shared rules' device document id pattern. */
        private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,128}$")

        fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)

        private val INSTALLATION_ID = stringPreferencesKey("notifications.installationId.v1")
        private val LEGACY_DEVICE_REMOVED = booleanPreferencesKey("notifications.legacyDeviceRemoved.v1")
    }
}
