package com.salticusteam.vifi.core.data.coins

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeUnlocksReader
import com.salticusteam.vifi.core.data.repository.fake.FakeWalletReader
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.model.CoinWallet
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoinStoresTest {

    // --- CoinWalletStore ---

    @Test
    fun `wallet when signed in follows the user's live balance`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthService(isSignedIn = true)
        val reader = FakeWalletReader(balance = 120)
        val store = CoinWalletStore(reader, SessionStore(auth, backgroundScope), backgroundScope)

        store.wallet.test {
            assertEquals(120, expectMostRecentItem().balance)
            reader.wallet.value = CoinWallet(balance = 20)
            assertEquals(20, awaitItem().balance)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(FakeAuthService.DEFAULT_USER.id), reader.observedUids)
    }

    @Test
    fun `wallet after sign out drops back to empty`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthService(isSignedIn = true)
        val store = CoinWalletStore(FakeWalletReader(balance = 120), SessionStore(auth, backgroundScope), backgroundScope)

        store.wallet.test {
            assertEquals(120, expectMostRecentItem().balance)
            auth.signOut()
            assertEquals(CoinWallet.EMPTY, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `wallet when signed out never opens a listener`() = runTest(UnconfinedTestDispatcher()) {
        val reader = FakeWalletReader()
        val store = CoinWalletStore(reader, SessionStore(FakeAuthService(isSignedIn = false), backgroundScope), backgroundScope)

        store.wallet.test {
            assertEquals(CoinWallet.EMPTY, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(reader.observedUids.isEmpty())
    }

    // --- ExamUnlocksStore ---

    @Test
    fun `unlocks when signed in loads the user's set`() = runTest(UnconfinedTestDispatcher()) {
        val reader = FakeUnlocksReader(ids = setOf("e1", "e2"))
        val store = ExamUnlocksStore(reader, SessionStore(FakeAuthService(), backgroundScope), backgroundScope)

        assertEquals(setOf("e1", "e2"), store.unlockedIds.value)
        assertEquals(listOf(FakeAuthService.DEFAULT_USER.id), reader.requestedUids)
    }

    @Test
    fun `unlocks when load fails stays empty`() = runTest(UnconfinedTestDispatcher()) {
        val reader = FakeUnlocksReader(ids = setOf("e1"), failure = IllegalStateException("denied"))
        val store = ExamUnlocksStore(reader, SessionStore(FakeAuthService(), backgroundScope), backgroundScope)

        assertTrue(store.unlockedIds.value.isEmpty())
    }

    @Test
    fun `markUnlocked adds the exam and sign out clears the set`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthService(isSignedIn = true, latency = Duration.ZERO)
        val store = ExamUnlocksStore(FakeUnlocksReader(ids = setOf("e1")), SessionStore(auth, backgroundScope), backgroundScope)

        store.markUnlocked("e9")
        assertTrue(store.isUnlocked("e9"))
        assertEquals(setOf("e1", "e9"), store.unlockedIds.value)

        auth.signOut()
        assertFalse(store.isUnlocked("e9"))
        assertTrue(store.unlockedIds.value.isEmpty())
    }
}
