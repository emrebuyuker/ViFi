package com.salticusteam.vifi.core.notifications

import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeDeviceTokenStore
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationService
import com.salticusteam.vifi.core.data.session.SessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PushTokenRegistrationTest {

    private val uid = FakeAuthService.DEFAULT_USER.id
    private val store = FakeDeviceTokenStore()

    private fun TestScope.startSut(auth: FakeAuthService = FakeAuthService()): PushTokenRegistration {
        val scope = backgroundScope
        return PushTokenRegistration(SessionStore(auth, scope), FakeNotificationService(), store, scope)
            .apply { start() }
    }

    @Test
    fun `signed-in start writes the current token once`() = runTest(UnconfinedTestDispatcher()) {
        val sut = startSut()
        sut.start()
        advanceUntilIdle()

        assertEquals(listOf(uid to "fake-token"), store.registrations)
    }

    @Test
    fun `signed out writes nothing, not even a new token`() = runTest(UnconfinedTestDispatcher()) {
        val sut = startSut(FakeAuthService(isSignedIn = false))

        sut.onNewToken("rotated")
        advanceUntilIdle()

        assertTrue(store.registrations.isEmpty())
    }

    @Test
    fun `sign in writes the token`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthService(isSignedIn = false)
        startSut(auth)

        val verificationId = auth.sendVerificationCode("+905321234567")
        auth.signIn(verificationId, FakeAuthService.VALID_CODE)
        advanceUntilIdle()

        assertEquals(listOf(uid to "fake-token"), store.registrations)
    }

    @Test
    fun `a rotated token is written, a repeated one is not`() = runTest(UnconfinedTestDispatcher()) {
        val sut = startSut()

        sut.onNewToken("rotated")
        sut.onNewToken("rotated")
        advanceUntilIdle()

        assertEquals(listOf(uid to "fake-token", uid to "rotated"), store.registrations)
    }

    @Test
    fun `a failed write is retried on the next token`() = runTest(UnconfinedTestDispatcher()) {
        store.failure = IllegalStateException("PERMISSION_DENIED")
        val sut = startSut()
        store.failure = null

        sut.onNewToken("fake-token")
        advanceUntilIdle()

        assertEquals(listOf(uid to "fake-token"), store.registrations)
    }
}
