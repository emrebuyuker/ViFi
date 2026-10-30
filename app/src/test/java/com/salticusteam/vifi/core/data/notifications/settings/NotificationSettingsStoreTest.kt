package com.salticusteam.vifi.core.data.notifications.settings

import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsState.Availability
import com.salticusteam.vifi.core.data.repository.NotificationSettingsService
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.model.NotificationConsent
import com.salticusteam.vifi.core.model.NotificationConsentSource
import com.salticusteam.vifi.core.model.NotificationSettingsError
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `NotificationSettingsStoreTests` (15). */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsStoreTest {

    private val service = ScriptedSettingsService()
    private val sessions = MutableStateFlow<SessionState>(signedIn("u-1"))

    private fun TestScope.sut() = NotificationSettingsStore(service, backgroundScope).apply { start(sessions) }

    private fun signedIn(uid: String) = SessionState.SignedIn(FakeAuthService.DEFAULT_USER.copy(id = uid))

    @Test
    fun `while loading nothing is known or on`() = runTest(UnconfinedTestDispatcher()) {
        val state = sut().state.value

        assertEquals(Availability.LOADING, state.availability)
        assertFalse(state.isLoaded)
        assertFalse(state.hasAnswered)
        assertFalse(state.marketingEnabled)
    }

    @Test
    fun `a user who never answered is loaded, unanswered and off`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        service.emit("u-1", null)

        assertTrue(sut.state.value.isLoaded)
        assertFalse(sut.state.value.hasAnswered)
        assertFalse(sut.state.value.marketingEnabled)
    }

    @Test
    fun `the store follows the record live`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        service.emit("u-1", NotificationConsent(marketing = true))
        assertTrue(sut.state.value.marketingEnabled)
        service.emit("u-1", NotificationConsent(marketing = false))

        assertTrue(sut.state.value.hasAnswered)
        assertFalse(sut.state.value.marketingEnabled)
    }

    @Test
    fun `an unreadable record is unavailable, never an answer`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        service.fail("u-1")

        assertEquals(Availability.UNAVAILABLE, sut.state.value.availability)
        assertFalse(sut.state.value.hasAnswered)
    }

    @Test
    fun `signing out forgets the answer and nothing is written without a user`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        service.emit("u-1", NotificationConsent(marketing = true))

        sessions.value = SessionState.SignedOut

        assertEquals(NotificationSettingsState(), sut.state.value)
        assertFalse(sut.setMarketing(true, NotificationConsentSource.SETTINGS))
        assertTrue(service.writes.isEmpty())
    }

    @Test
    fun `another user starts over until their own record arrives`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        service.emit("u-1", NotificationConsent(marketing = true))

        sessions.value = signedIn("u-2")
        assertEquals(NotificationSettingsState(), sut.state.value)
        service.emit("u-2", null)

        assertTrue(sut.state.value.isLoaded)
        assertFalse(sut.state.value.marketingEnabled)
    }

    @Test
    fun `a previous user's late value is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        sessions.value = SessionState.SignedOut

        service.emit("u-1", NotificationConsent(marketing = true))

        assertEquals(NotificationSettingsState(), sut.state.value)
    }

    @Test
    fun `a previous user's late failure is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        sessions.value = signedIn("u-2")
        service.emit("u-2", NotificationConsent(marketing = true))

        service.fail("u-1")

        assertTrue(sut.state.value.marketingEnabled)
    }

    @Test
    fun `waiting returns as soon as the record is read`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        val wait = async { sut.waitUntilLoaded() }
        service.emit("u-1", null)

        assertTrue(wait.await())
        assertEquals(0L, currentTime)
    }

    @Test
    fun `waiting gives up after its time`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        assertFalse(sut.waitUntilLoaded(timeoutMillis = 2_000))
        assertEquals(2_000L, currentTime)
    }

    @Test
    fun `an answer is written with its source for the user and shown at once`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        service.emit("u-1", null)

        assertTrue(sut.setMarketing(true, NotificationConsentSource.PROMPT))

        assertEquals(listOf(Write("u-1", true, NotificationConsentSource.PROMPT)), service.writes)
        assertTrue(sut.state.value.marketingEnabled)
    }

    @Test
    fun `a change from the settings is written as such`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        service.emit("u-1", NotificationConsent(marketing = true))

        assertTrue(sut.setMarketing(false, NotificationConsentSource.SETTINGS))

        assertEquals(listOf(Write("u-1", false, NotificationConsentSource.SETTINGS)), service.writes)
        assertFalse(sut.state.value.marketingEnabled)
    }

    @Test
    fun `a rejected write puts the previous answer back`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        service.emit("u-1", NotificationConsent(marketing = true))
        service.writeFailure = IOException("PERMISSION_DENIED")

        assertFalse(sut.setMarketing(false, NotificationConsentSource.SETTINGS))

        assertEquals(NotificationConsent(marketing = true), sut.state.value.consent)
    }

    @Test
    fun `a rejected first answer leaves the user unanswered`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        service.emit("u-1", null)
        service.writeFailure = IOException("PERMISSION_DENIED")

        assertFalse(sut.setMarketing(true, NotificationConsentSource.PROMPT))

        assertFalse(sut.state.value.hasAnswered)
    }

    @Test
    fun `a write not acknowledged in four seconds counts as saved and stays shown`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        service.emit("u-1", null)
        service.hangWrites = true

        val saved = async { sut.setMarketing(true, NotificationConsentSource.PROMPT) }
        assertTrue(sut.state.value.marketingEnabled)
        advanceTimeBy(4_001)

        assertTrue(saved.await())
        assertTrue(sut.state.value.marketingEnabled)
    }

    private data class Write(val uid: String, val marketing: Boolean, val source: NotificationConsentSource)

    /** Each user's record driven by the test; a failure ends that user's flow, as the real listener does. */
    private class ScriptedSettingsService : NotificationSettingsService {

        private val records = mutableMapOf<String, MutableSharedFlow<Result<NotificationConsent?>>>()
        val writes = mutableListOf<Write>()
        var writeFailure: Exception? = null
        var hangWrites = false

        private fun record(uid: String) = records.getOrPut(uid) { MutableSharedFlow(replay = 1) }

        fun emit(uid: String, consent: NotificationConsent?) = record(uid).tryEmit(Result.success(consent))

        fun fail(uid: String) = record(uid).tryEmit(Result.failure(NotificationSettingsError.Unavailable))

        override fun consentChanges(uid: String): Flow<NotificationConsent?> = record(uid).map { it.getOrThrow() }

        override suspend fun setMarketing(uid: String, enabled: Boolean, source: NotificationConsentSource) {
            if (hangWrites) awaitCancellation()
            writeFailure?.let { throw it }
            writes += Write(uid, enabled, source)
        }
    }
}
