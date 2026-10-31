package com.salticusteam.vifi.core.data.inbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 4.0.0's per-device read ids, dropped once an account's first server marker is written (iOS `migratesLegacyState`). */
class LegacyInboxReadStateTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val file by lazy { tmp.newFile("legacy.preferences_pb").also { it.delete() } }

    private fun TestScope.dataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        produceFile = { file },
    )

    @Test
    fun `clearing drops 4_0_0's read ids and nothing else`() = runTest {
        val dataStore = dataStore()
        dataStore.edit {
            it[LEGACY_READ_IDS] = setOf("a1", "a2")
            it[OTHER] = true
        }

        DataStoreLegacyInboxReadState(dataStore).clear()

        val stored = dataStore.data.first()
        assertNull(stored[LEGACY_READ_IDS])
        assertEquals(true, stored[OTHER])
    }

    @Test
    fun `clearing a device that never had them is harmless`() = runTest {
        val dataStore = dataStore()

        DataStoreLegacyInboxReadState(dataStore).clear()

        assertNull(dataStore.data.first()[LEGACY_READ_IDS])
    }

    private companion object {
        /** The key 4.0.0's `AnnouncementReadPreferences` wrote (`v-4.0.0`). */
        val LEGACY_READ_IDS = stringSetPreferencesKey("announcements.readIds")
        val OTHER = booleanPreferencesKey("unrelated.flag")
    }
}
