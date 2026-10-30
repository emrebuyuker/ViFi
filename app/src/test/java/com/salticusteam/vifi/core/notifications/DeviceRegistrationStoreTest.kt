package com.salticusteam.vifi.core.notifications

import com.salticusteam.vifi.core.data.repository.DeviceRegistrar
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeDeviceRegistrar
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationService
import com.salticusteam.vifi.core.data.session.SessionState
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `DeviceRegistrationStoreTests` (11) plus the Android start-up and sign-out ordering. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRegistrationStoreTest {

    private val registrar = FakeDeviceRegistrar()
    private val uid = FakeAuthService.DEFAULT_USER.id

    private fun TestScope.sut(registrar: DeviceRegistrar = this@DeviceRegistrationStoreTest.registrar) =
        DeviceRegistrationStore(registrar, FakeNotificationService(), backgroundScope)

    @Test
    fun `a token before sign-in registers on sign-in`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        sut.tokenDidChange("t-1")
        assertTrue(registrar.registrations.isEmpty())
        sut.userDidSignIn(uid)

        assertEquals(listOf(uid to "t-1"), registrar.registrations)
    }

    @Test
    fun `a sign-in before the token registers when the token arrives`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        sut.userDidSignIn(uid)
        assertTrue(registrar.registrations.isEmpty())
        sut.tokenDidChange("t-1")

        assertEquals(listOf(uid to "t-1"), registrar.registrations)
    }

    @Test
    fun `the same pair registers once`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        sut.userDidSignIn(uid)
        sut.tokenDidChange("t-1")
        sut.tokenDidChange("t-1")
        sut.userDidSignIn(uid)

        assertEquals(listOf(uid to "t-1"), registrar.registrations)
    }

    @Test
    fun `a refreshed token registers again`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        sut.userDidSignIn(uid)

        sut.tokenDidChange("t-1")
        sut.tokenDidChange("t-2")

        assertEquals(listOf(uid to "t-1", uid to "t-2"), registrar.registrations)
    }

    @Test
    fun `a blank or missing token is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        sut.userDidSignIn(uid)

        sut.tokenDidChange("  ")
        sut.tokenDidChange(null)
        sut.tokenDidChange(" t-1 ")

        assertEquals(listOf(uid to "t-1"), registrar.registrations)
    }

    @Test
    fun `another user registers the token under them`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        sut.tokenDidChange("t-1")

        sut.userDidSignIn("u-1")
        sut.userDidSignOut()
        sut.userDidSignIn("u-2")

        assertEquals(listOf("u-1" to "t-1", "u-2" to "t-1"), registrar.registrations)
    }

    @Test
    fun `nothing is written while signed out`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        sut.userDidSignIn(uid)
        sut.userDidSignOut()

        sut.tokenDidChange("t-1")

        assertTrue(registrar.registrations.isEmpty())
    }

    @Test
    fun `a failed write is retried on the next trigger`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        registrar.registerFailure = IOException("offline")
        sut.userDidSignIn(uid)
        sut.tokenDidChange("t-1")
        assertTrue(registrar.registrations.isEmpty())

        registrar.registerFailure = null
        sut.userDidSignIn(uid)

        assertEquals(listOf(uid to "t-1"), registrar.registrations)
    }

    @Test
    fun `unregister removes the device and stops registering`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        sut.userDidSignIn(uid)
        sut.tokenDidChange("t-1")

        sut.unregister(uid)
        sut.tokenDidChange("t-2")

        assertEquals(listOf(uid), registrar.unregistrations)
        assertEquals(listOf(uid to "t-1"), registrar.registrations)
        assertTrue(registrar.tokens.isEmpty())
    }

    @Test
    fun `a failed removal is swallowed`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        registrar.unregisterFailure = IOException("offline")

        sut.unregister(uid)

        assertTrue(registrar.unregistrations.isEmpty())
    }

    @Test
    fun `a hanging removal gives up after three seconds`() = runTest(UnconfinedTestDispatcher()) {
        val hanging = object : DeviceRegistrar {
            override suspend fun register(uid: String, token: String) = Unit
            override suspend fun unregister(uid: String) = awaitCancellation()
        }
        val sut = sut(hanging)

        sut.unregister(uid)

        assertEquals(3_000L, currentTime)
    }

    // --- Android ---

    @Test
    fun `start reads the current token and follows the session`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        val sessions = MutableStateFlow<SessionState>(SessionState.Unknown)

        sut.start(sessions)
        sut.start(MutableStateFlow(SessionState.SignedIn(FakeAuthService.DEFAULT_USER)))
        assertTrue(registrar.registrations.isEmpty())
        sessions.value = SessionState.SignedIn(FakeAuthService.DEFAULT_USER)
        sessions.value = SessionState.SignedOut
        sut.tokenDidChange("rotated")

        assertEquals(listOf(uid to "fake-token"), registrar.registrations)
    }

    @Test
    fun `unregister waits for a write still landing before removing`() = runTest(UnconfinedTestDispatcher()) {
        val writeLanded = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val slow = object : DeviceRegistrar {
            override suspend fun register(uid: String, token: String) {
                writeLanded.await()
                order += "register"
            }

            override suspend fun unregister(uid: String) {
                order += "unregister"
            }
        }
        val sut = sut(slow)
        sut.userDidSignIn(uid)
        sut.tokenDidChange("t-1")

        val removal = launch { sut.unregister(uid) }
        assertTrue(order.isEmpty())
        writeLanded.complete(Unit)
        advanceUntilIdle()

        assertTrue(removal.isCompleted)
        assertEquals(listOf("register", "unregister"), order)
    }
}
