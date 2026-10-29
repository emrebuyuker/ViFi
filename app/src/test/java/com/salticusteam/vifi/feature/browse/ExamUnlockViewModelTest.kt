package com.salticusteam.vifi.feature.browse

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.billing.SubscriptionStore
import com.salticusteam.vifi.core.data.coins.CoinWalletStore
import com.salticusteam.vifi.core.data.coins.ExamUnlocksStore
import com.salticusteam.vifi.core.data.repository.ExamPurchaseService
import com.salticusteam.vifi.core.data.repository.ExamUnlockService
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeExamPurchaseService
import com.salticusteam.vifi.core.data.repository.fake.FakeExamUnlockService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubscriptionService
import com.salticusteam.vifi.core.data.repository.fake.FakeUnlocksReader
import com.salticusteam.vifi.core.data.repository.fake.FakeWalletReader
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamLock
import com.salticusteam.vifi.core.model.ExamSummary
import com.salticusteam.vifi.core.model.ExamUnlockError
import com.salticusteam.vifi.core.model.ExamUnlockResult
import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.SubscriptionError
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExamUnlockViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val lesson = ArchivePath.ROOT.appending("u").appending("f").appending("d").appending("l")
    private val locked = ArchiveItem(lesson.appending("e9", "2023 Final"), ArchiveLevel.EXAM, null, ExamSummary(ExamKind.PDF, 1))

    private lateinit var analytics: FakeAnalyticsTracker
    private lateinit var unlocksStore: ExamUnlocksStore

    /** Runs pending work, including the stores' listeners in backgroundScope that advanceUntilIdle skips. */
    private fun TestScope.settle() {
        advanceUntilIdle()
        runCurrent()
    }

    private fun TestScope.makeSut(
        balance: Int = 120,
        service: ExamUnlockService = FakeExamUnlockService(),
        purchaseService: ExamPurchaseService = FakeExamPurchaseService(),
        subscriptionService: FakeSubscriptionService = FakeSubscriptionService(),
    ): ExamUnlockViewModel {
        val scope = backgroundScope
        val session = SessionStore(FakeAuthService(), scope)
        analytics = FakeAnalyticsTracker()
        unlocksStore = ExamUnlocksStore(FakeUnlocksReader(ids = setOf("e1")), session, scope)
        return ExamUnlockViewModel(
            walletStore = CoinWalletStore(FakeWalletReader(balance), session, scope),
            unlocksStore = unlocksStore,
            subscriptionStore = SubscriptionStore(subscriptionService, scope),
            unlockService = service,
            purchaseService = purchaseService,
            analytics = analytics,
        )
    }

    @Test
    fun `init mirrors the shared balance and unlocks`() = runTest {
        val sut = makeSut(balance = 120)
        settle()

        assertEquals(120, sut.uiState.value.balance)
        assertEquals(setOf("e1"), sut.uiState.value.unlockedIds)
        assertTrue(sut.uiState.value.canAfford)
    }

    @Test
    fun `onLockedExamTap shows the sheet and reports whether the user can afford it`() = runTest {
        val sut = makeSut(balance = 20)
        settle()

        sut.onLockedExamTap(locked)

        assertEquals(locked, sut.uiState.value.examToUnlock)
        assertFalse(sut.uiState.value.canAfford)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.ExamUnlockSheetShown(canAfford = false)), analytics.events)
    }

    @Test
    fun `onUnlockConfirm when success marks unlocked, closes the sheet and emits Unlocked`() = runTest {
        val service = FakeExamUnlockService()
        val sut = makeSut(service = service)
        settle()
        sut.onLockedExamTap(locked)

        sut.events.test {
            sut.onUnlockConfirm()
            assertTrue(sut.uiState.value.isUnlocking)

            assertEquals(ExamUnlockEvent.Unlocked(locked), awaitItem())
            val state = sut.uiState.value
            assertFalse(state.isUnlocking)
            assertNull(state.examToUnlock)
            assertTrue(unlocksStore.isUnlocked("e9"))
            assertEquals(listOf("e9"), service.unlockedExamIds)
            assertEquals(
                AnalyticsEvent.ExamUnlocked(ExamKind.PDF, ExamLock.UNLOCK_COST, alreadyUnlocked = false),
                analytics.events.last(),
            )
        }
    }

    @Test
    fun `onUnlockConfirm when insufficient coins keeps the sheet with the error`() = runTest {
        val sut = makeSut(service = FakeExamUnlockService(failure = ExamUnlockError.InsufficientCoins))
        settle()
        sut.onLockedExamTap(locked)

        sut.onUnlockConfirm()
        settle()

        val state = sut.uiState.value
        assertFalse(state.isUnlocking)
        assertEquals(locked, state.examToUnlock)
        assertEquals(ExamUnlockError.InsufficientCoins.userMessage, state.error)
        assertFalse(unlocksStore.isUnlocked("e9"))
        assertEquals(AnalyticsEvent.ExamUnlockFailed("insufficient_coins"), analytics.events.last())
    }

    @Test
    fun `onUnlockConfirm when already unlocked is still a success`() = runTest {
        val service = object : ExamUnlockService {
            override suspend fun unlock(examId: String) = ExamUnlockResult(balance = 120, alreadyUnlocked = true)
        }
        val sut = makeSut(service = service)
        settle()
        sut.onLockedExamTap(locked)

        sut.events.test {
            sut.onUnlockConfirm()
            assertEquals(ExamUnlockEvent.Unlocked(locked), awaitItem())
        }
        assertEquals(
            AnalyticsEvent.ExamUnlocked(ExamKind.PDF, ExamLock.UNLOCK_COST, alreadyUnlocked = true),
            analytics.events.last(),
        )
    }

    @Test
    fun `onSheetDismiss while unlocking is ignored and a second confirm does not call again`() = runTest {
        val gate = CompletableDeferred<ExamUnlockResult>()
        var calls = 0
        val service = object : ExamUnlockService {
            override suspend fun unlock(examId: String): ExamUnlockResult {
                calls++
                return gate.await()
            }
        }
        val sut = makeSut(service = service)
        settle()
        sut.onLockedExamTap(locked)

        sut.onUnlockConfirm()
        settle()
        sut.onUnlockConfirm()
        sut.onSheetDismiss()
        settle()

        assertEquals(1, calls)
        assertEquals(locked, sut.uiState.value.examToUnlock)

        gate.complete(ExamUnlockResult(balance = 20, alreadyUnlocked = false))
        settle()
        assertNull(sut.uiState.value.examToUnlock)
    }

    @Test
    fun `onEarnCoins closes the sheet, reports and emits EarnCoins with the exam's lesson`() = runTest {
        val sut = makeSut(balance = 0)
        settle()
        sut.onLockedExamTap(locked)

        sut.events.test {
            sut.onEarnCoins()
            assertEquals(ExamUnlockEvent.EarnCoins(lesson = locked.path.parent), awaitItem())
        }
        assertNull(sut.uiState.value.examToUnlock)
        assertEquals(AnalyticsEvent.EarnCoinsTapped, analytics.events.last())
    }

    @Test
    fun `lockedPaths uses the full list and honours unlocks`() {
        val exams = listOf("e4", "e3", "e2", "e1").map {
            ArchiveItem(lesson.appending(it), ArchiveLevel.EXAM, null, ExamSummary(ExamKind.IMAGES, 2))
        }

        val locked = ExamUnlockUiState(unlockedIds = setOf("e3")).lockedPaths(exams)

        assertEquals(setOf(lesson.appending("e4")), locked)
        assertTrue(ExamUnlockUiState().lockedPaths(null).isEmpty())
    }

    // --- Premium (4.0.0 UI-03) ---

    @Test
    fun `init mirrors an active subscription and nothing is locked for a subscriber`() = runTest {
        val subscription = FakeSubscriptionService(subscribed = true)
        val sut = makeSut(subscriptionService = subscription)
        settle()

        val exams = listOf("e4", "e3", "e2", "e1").map {
            ArchiveItem(lesson.appending(it), ArchiveLevel.EXAM, null, ExamSummary(ExamKind.IMAGES, 2))
        }
        assertTrue(sut.uiState.value.isSubscribed)
        assertTrue(sut.uiState.value.lockedPaths(exams).isEmpty())

        // A lapsed renewal brings the locks back.
        subscription.setSubscribed(false)
        settle()
        assertFalse(sut.uiState.value.isSubscribed)
        assertEquals(setOf(lesson.appending("e4"), lesson.appending("e3")), sut.uiState.value.lockedPaths(exams))
    }

    // --- Buying with money (4.0.0 UI-02) ---

    @Test
    fun `onPurchaseWithMoney when recorded marks unlocked, closes the sheet and emits Unlocked`() = runTest {
        val purchases = FakeExamPurchaseService()
        val sut = makeSut(balance = 0, purchaseService = purchases)
        settle()
        sut.onLockedExamTap(locked)

        sut.events.test {
            sut.onPurchaseWithMoney()
            assertTrue(sut.uiState.value.isPurchasing)
            assertTrue(sut.uiState.value.isBusy)

            assertEquals(ExamUnlockEvent.Unlocked(locked), awaitItem())
            val state = sut.uiState.value
            assertFalse(state.isPurchasing)
            assertNull(state.examToUnlock)
            assertTrue(unlocksStore.isUnlocked("e9"))
            assertEquals(listOf("e9"), purchases.purchasedExamIds)
        }
    }

    @Test
    fun `onPurchaseWithMoney when cancelled keeps the sheet without a message`() = runTest {
        val sut = makeSut(purchaseService = FakeExamPurchaseService(purchaseResult = PurchaseResult.CANCELLED))
        settle()
        sut.onLockedExamTap(locked)

        sut.onPurchaseWithMoney()
        settle()

        val state = sut.uiState.value
        assertFalse(state.isPurchasing)
        assertEquals(locked, state.examToUnlock)
        assertNull(state.error)
        assertFalse(state.isPurchasePending)
        assertFalse(unlocksStore.isUnlocked("e9"))
    }

    @Test
    fun `onPurchaseWithMoney when pending keeps the sheet and says the exam opens later`() = runTest {
        val sut = makeSut(purchaseService = FakeExamPurchaseService(purchaseResult = PurchaseResult.PENDING))
        settle()
        sut.onLockedExamTap(locked)

        sut.onPurchaseWithMoney()
        settle()

        val state = sut.uiState.value
        assertFalse(state.isPurchasing)
        assertEquals(locked, state.examToUnlock)
        assertTrue(state.isPurchasePending)
        assertFalse(unlocksStore.isUnlocked("e9"))

        // Showing the sheet again starts without the old notice.
        sut.onSheetDismiss()
        sut.onLockedExamTap(locked)
        assertFalse(sut.uiState.value.isPurchasePending)
    }

    @Test
    fun `a pending purchase that completes closes the sheet and emits Unlocked`() = runTest {
        val sut = makeSut(purchaseService = FakeExamPurchaseService(purchaseResult = PurchaseResult.PENDING))
        settle()
        sut.onLockedExamTap(locked)
        sut.onPurchaseWithMoney()
        settle()

        sut.events.test {
            // Recovery recorded the paid unlock when the user came back to the app.
            unlocksStore.markUnlocked("e9")

            assertEquals(ExamUnlockEvent.Unlocked(locked), awaitItem())
            val state = sut.uiState.value
            assertNull(state.examToUnlock)
            assertFalse(state.isPurchasePending)
        }
    }

    @Test
    fun `another exam unlocking while a payment is pending keeps the sheet`() = runTest {
        val sut = makeSut(purchaseService = FakeExamPurchaseService(purchaseResult = PurchaseResult.PENDING))
        settle()
        sut.onLockedExamTap(locked)
        sut.onPurchaseWithMoney()
        settle()

        sut.events.test {
            unlocksStore.markUnlocked("e7")
            settle()

            expectNoEvents()
            assertEquals(locked, sut.uiState.value.examToUnlock)
            assertTrue(sut.uiState.value.isPurchasePending)
        }
    }

    @Test
    fun `onPurchaseWithMoney when Google Play fails shows its message`() = runTest {
        val sut = makeSut(purchaseService = FakeExamPurchaseService(failure = SubscriptionError.FailedVerification))
        settle()
        sut.onLockedExamTap(locked)

        sut.onPurchaseWithMoney()
        settle()

        val state = sut.uiState.value
        assertFalse(state.isPurchasing)
        assertEquals(locked, state.examToUnlock)
        assertEquals(SubscriptionError.FailedVerification.userMessage, state.error)
    }

    @Test
    fun `while a purchase runs the coin unlock, earn, Premium and dismiss are ignored`() = runTest {
        val gate = CompletableDeferred<PurchaseResult>()
        var calls = 0
        val purchases = object : ExamPurchaseService {
            override suspend fun purchaseUnlock(examId: String): PurchaseResult {
                calls++
                return gate.await()
            }

            override suspend fun recoverPendingUnlock(): String? = null
        }
        val coins = FakeExamUnlockService()
        val sut = makeSut(service = coins, purchaseService = purchases)
        settle()
        sut.onLockedExamTap(locked)

        sut.events.test {
            sut.onPurchaseWithMoney()
            settle()
            sut.onPurchaseWithMoney()
            sut.onUnlockConfirm()
            sut.onEarnCoins()
            sut.onGoPremium()
            sut.onSheetDismiss()
            settle()

            assertEquals(1, calls)
            assertTrue(coins.unlockedExamIds.isEmpty())
            assertEquals(locked, sut.uiState.value.examToUnlock)
            expectNoEvents()

            gate.complete(PurchaseResult.CANCELLED)
            settle()
            assertFalse(sut.uiState.value.isBusy)
        }
    }

    @Test
    fun `while a coin unlock runs buying with money is ignored`() = runTest {
        val gate = CompletableDeferred<ExamUnlockResult>()
        val coins = object : ExamUnlockService {
            override suspend fun unlock(examId: String): ExamUnlockResult = gate.await()
        }
        val purchases = FakeExamPurchaseService()
        val sut = makeSut(service = coins, purchaseService = purchases)
        settle()
        sut.onLockedExamTap(locked)

        sut.onUnlockConfirm()
        settle()
        sut.onPurchaseWithMoney()
        settle()

        assertFalse(sut.uiState.value.isPurchasing)
        assertTrue(purchases.purchasedExamIds.isEmpty())
        gate.complete(ExamUnlockResult(balance = 20, alreadyUnlocked = false))
        settle()
    }

    @Test
    fun `onGoPremium closes the sheet and emits GoPremium`() = runTest {
        val sut = makeSut()
        settle()
        sut.onLockedExamTap(locked)

        sut.events.test {
            sut.onGoPremium()
            assertEquals(ExamUnlockEvent.GoPremium, awaitItem())
        }
        assertNull(sut.uiState.value.examToUnlock)
    }
}
