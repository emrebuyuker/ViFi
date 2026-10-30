package com.salticusteam.vifi.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** iOS `NotificationPromptHistoryTests` (8) plus 4.0.0's one-time prompt. */
class NotificationPromptHistoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = MutableClock(Instant.parse("2026-10-30T09:00:00Z"))
    private val file by lazy { tmp.newFile("prompts.preferences_pb").also { it.delete() } }
    private var store: DataStore<Preferences>? = null

    private fun TestScope.dataStore(): DataStore<Preferences> = store ?: PreferenceDataStoreFactory.create(
        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        produceFile = { file },
    ).also { store = it }

    private fun TestScope.sut() = DataStoreNotificationPromptHistory(dataStore(), clock)

    @Test
    fun `a fresh device was offered nothing and has nothing to re-ask`() = runTest {
        val sut = sut()

        assertFalse(sut.hasOfferedPriming())
        assertFalse(sut.hasOfferedConsent())
        assertFalse(sut.shouldReaskPermission())
        assertFalse(sut.shouldReaskConsent())
    }

    @Test
    fun `offers are remembered on the device`() = runTest {
        sut().markPrimingOffered()
        sut().markConsentOffered()

        assertTrue(sut().hasOfferedPriming())
        assertTrue(sut().hasOfferedConsent())
    }

    @Test
    fun `the permission re-ask is due 30 days after the no and stays due`() = runTest {
        val sut = sut()
        sut.recordPermissionDeclined()

        clock.advance(Duration.ofDays(30).minusHours(1))
        assertFalse(sut.shouldReaskPermission())
        clock.advance(Duration.ofHours(1))
        assertTrue(sut.shouldReaskPermission())
        clock.advance(Duration.ofDays(90))
        assertTrue(sut.shouldReaskPermission())
    }

    @Test
    fun `the permission re-ask is shown once and a later no does not arm it again`() = runTest {
        val sut = sut()
        sut.recordPermissionDeclined()
        clock.advance(Duration.ofDays(31))

        sut.markPermissionReaskShown()
        sut.recordPermissionDeclined()
        clock.advance(Duration.ofDays(400))

        assertFalse(sut.shouldReaskPermission())
    }

    @Test
    fun `the first no wins, a later one cannot postpone the re-ask`() = runTest {
        val sut = sut()
        sut.recordPermissionDeclined()
        clock.advance(Duration.ofDays(20))
        sut.recordPermissionDeclined()

        clock.advance(Duration.ofDays(10))

        assertTrue(sut.shouldReaskPermission())
    }

    @Test
    fun `offering alone never arms a re-ask`() = runTest {
        val sut = sut()
        sut.markPrimingOffered()
        sut.markConsentOffered()

        clock.advance(Duration.ofDays(400))

        assertFalse(sut.shouldReaskPermission())
        assertFalse(sut.shouldReaskConsent())
    }

    @Test
    fun `the consent follows the same rules, apart from the permission`() = runTest {
        val sut = sut()
        sut.recordConsentDeclined()
        clock.advance(Duration.ofDays(30))

        assertTrue(sut.shouldReaskConsent())
        assertFalse(sut.shouldReaskPermission())
        sut.markConsentReaskShown()
        assertFalse(sut.shouldReaskConsent())
    }

    @Test
    fun `the delay is thirty days`() {
        assertEquals(Duration.ofDays(30), NotificationPromptHistory.REASK_DELAY)
    }

    @Test
    fun `4_0_0's one-time prompt counts as the permission step's offer`() = runTest {
        dataStore().edit { it[booleanPreferencesKey("notifications.prompted")] = true }

        assertTrue(sut().hasOfferedPriming())
        assertFalse(sut().hasOfferedConsent())
    }

    private class MutableClock(private var now: Instant) : Clock() {
        fun advance(by: Duration) {
            now = now.plus(by)
        }

        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
}
