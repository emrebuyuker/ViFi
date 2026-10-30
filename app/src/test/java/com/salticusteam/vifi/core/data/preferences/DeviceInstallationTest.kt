package com.salticusteam.vifi.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** iOS `FirebaseDeviceRegistrarTests`' installation id cases. */
class DeviceInstallationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var counter = 0

    private fun TestScope.newStore(): DataStore<Preferences> {
        val file = tmp.newFile("d${counter++}.preferences_pb").also { it.delete() }
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            produceFile = { file },
        )
    }

    @Test
    fun `the installation id is stable for the install`() = runTest {
        val store = newStore()

        val first = DeviceInstallation(store).id()
        val again = DeviceInstallation(store).id()

        assertEquals(first, again)
        assertTrue(DeviceInstallation.isValidId(first))
        assertEquals(first.lowercase(), first)
    }

    @Test
    fun `every install gets its own id`() = runTest {
        assertNotEquals(DeviceInstallation(newStore()).id(), DeviceInstallation(newStore()).id())
    }

    @Test
    fun `a stored id outside the rules' pattern is replaced`() = runTest {
        val store = newStore()
        store.edit { it[stringPreferencesKey("notifications.installationId.v1")] = "has space/and slash" }

        val id = DeviceInstallation(store).id()

        assertTrue(DeviceInstallation.isValidId(id))
        assertEquals(id, DeviceInstallation(store).id())
    }

    @Test
    fun `id validity follows the rules' pattern`() {
        assertTrue(DeviceInstallation.isValidId("abc-DEF_123"))
        assertTrue(DeviceInstallation.isValidId("a".repeat(128)))
        assertFalse(DeviceInstallation.isValidId("a".repeat(129)))
        assertFalse(DeviceInstallation.isValidId(""))
        assertFalse(DeviceInstallation.isValidId("has space"))
        assertFalse(DeviceInstallation.isValidId("slash/id"))
        assertFalse(DeviceInstallation.isValidId("ünicode"))
    }

    @Test
    fun `the legacy device removal is remembered`() = runTest {
        val store = newStore()

        assertFalse(DeviceInstallation(store).isLegacyDeviceRemoved())
        DeviceInstallation(store).markLegacyDeviceRemoved()

        assertTrue(DeviceInstallation(store).isLegacyDeviceRemoved())
    }
}
