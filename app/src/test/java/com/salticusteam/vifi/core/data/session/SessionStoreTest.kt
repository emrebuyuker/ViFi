package com.salticusteam.vifi.core.data.session

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionStoreTest {

    @Test
    fun `state when a user is stored starts signed in and follows sign out`() = runTest {
        val auth = FakeAuthService(isSignedIn = true)
        val store = SessionStore(auth, backgroundScope)

        store.state.test {
            assertEquals(SessionState.SignedIn(FakeAuthService.DEFAULT_USER), awaitItem())
            auth.signOut()
            assertEquals(SessionState.SignedOut, awaitItem())
            assertNull(store.user)
        }
    }

    @Test
    fun `state when nobody is stored starts unknown then reports signed out`() = runTest {
        val auth = FakeAuthService(isSignedIn = false)
        val store = SessionStore(auth, backgroundScope)

        store.state.test {
            assertEquals(SessionState.Unknown, awaitItem())
            assertEquals(SessionState.SignedOut, awaitItem())
        }
    }

    @Test
    fun `state after sign in is signed in with that user`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthService(isSignedIn = false, latency = kotlin.time.Duration.ZERO)
        val store = SessionStore(auth, backgroundScope)

        val id = auth.sendVerificationCode("+905551112233")
        val user = auth.signIn(id, FakeAuthService.VALID_CODE)

        assertEquals(SessionState.SignedIn(user), store.state.value)
        assertEquals("+905551112233", store.user?.phoneNumber)
    }
}
