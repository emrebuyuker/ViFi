package com.salticusteam.vifi.feature.premium

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.billing.SubscriptionStore
import com.salticusteam.vifi.core.data.repository.SubscriptionService
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeSubscriptionService
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.StoreProductIds
import com.salticusteam.vifi.core.model.SubscriptionError
import com.salticusteam.vifi.core.model.SubscriptionOption
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val analytics = FakeAnalyticsTracker()
    private val monthly = FakeSubscriptionService.SAMPLE_OPTIONS.first()
    private val yearly = FakeSubscriptionService.SAMPLE_OPTIONS.last()

    private lateinit var store: SubscriptionStore

    private fun TestScope.makeSut(service: SubscriptionService = FakeSubscriptionService()): PaywallViewModel {
        store = SubscriptionStore(service, backgroundScope)
        return PaywallViewModel(store, analytics)
    }

    /** Runs pending work, including the store's status listener in backgroundScope that advanceUntilIdle skips. */
    private fun TestScope.settle() {
        advanceUntilIdle()
        runCurrent()
    }

    // --- Plans ---

    @Test
    fun `init loads the plans and selects the yearly plan`() = runTest {
        val sut = makeSut()
        assertNull(sut.uiState.value.options)

        settle()

        val state = sut.uiState.value
        assertEquals(FakeSubscriptionService.SAMPLE_OPTIONS, state.options)
        assertEquals(StoreProductIds.YEARLY_PLAN, state.selectedPlanId)
        assertEquals(yearly, state.selectedOption)
        assertTrue(state.canSubscribe)
        assertFalse(state.loadFailed)
    }

    @Test
    fun `plans read earlier show at once`() = runTest {
        store = SubscriptionStore(FakeSubscriptionService(latency = Duration.ZERO), backgroundScope)
        store.loadOptions()

        val sut = PaywallViewModel(store, analytics)

        assertEquals(FakeSubscriptionService.SAMPLE_OPTIONS, sut.uiState.value.options)
        assertEquals(StoreProductIds.YEARLY_PLAN, sut.uiState.value.selectedPlanId)
    }

    @Test
    fun `a failed load offers the retry and onRetry loads the plans`() = runTest {
        val service = SwitchableService(optionsFailure = SubscriptionError.StoreUnavailable)
        val sut = makeSut(service)
        settle()
        assertTrue(sut.uiState.value.loadFailed)
        assertNull(sut.uiState.value.options)
        assertFalse(sut.uiState.value.canSubscribe)

        service.optionsFailure = null
        sut.onRetry()
        assertFalse(sut.uiState.value.loadFailed)
        settle()

        assertEquals(FakeSubscriptionService.SAMPLE_OPTIONS, sut.uiState.value.options)
        assertEquals(StoreProductIds.YEARLY_PLAN, sut.uiState.value.selectedPlanId)
    }

    @Test
    fun `no plan from Google Play offers the retry instead of an empty screen`() = runTest {
        val sut = makeSut(FakeSubscriptionService(options = emptyList()))
        settle()

        assertTrue(sut.uiState.value.loadFailed)
        assertNull(sut.uiState.value.selectedOption)
    }

    @Test
    fun `onScreenShown reports the Paywall screen`() = runTest {
        val sut = makeSut()

        sut.onScreenShown()

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.ScreenView(AnalyticsScreen.PAYWALL)), analytics.events)
    }

    // --- Subscribing ---

    @Test
    fun `onSubscribe buys the selected plan and closes once the subscription is active`() = runTest {
        val service = FakeSubscriptionService()
        val sut = makeSut(service)
        settle()
        sut.onPlanSelected(StoreProductIds.MONTHLY_PLAN)

        sut.events.test {
            sut.onSubscribe()
            assertTrue(sut.uiState.value.isPurchasing)
            assertFalse(sut.uiState.value.canSubscribe)

            assertEquals(PaywallEvent.Subscribed, awaitItem())
        }
        assertEquals(listOf(monthly), service.purchased)
        assertTrue(store.isSubscribed.value)
    }

    @Test
    fun `onSubscribe when cancelled stays without a message`() = runTest {
        val sut = makeSut(FakeSubscriptionService(purchaseResult = PurchaseResult.CANCELLED))
        settle()

        sut.events.test {
            sut.onSubscribe()
            settle()
            expectNoEvents()
        }
        val state = sut.uiState.value
        assertFalse(state.isPurchasing)
        assertNull(state.error)
        assertNull(state.notice)
    }

    @Test
    fun `onSubscribe when pending says the subscription starts once approved`() = runTest {
        val sut = makeSut(FakeSubscriptionService(purchaseResult = PurchaseResult.PENDING))
        settle()

        sut.onSubscribe()
        settle()

        assertFalse(sut.uiState.value.isPurchasing)
        assertEquals(PaywallNotice.PURCHASE_PENDING, sut.uiState.value.notice)
    }

    @Test
    fun `a pending payment that completes later closes the paywall`() = runTest {
        val service = FakeSubscriptionService(purchaseResult = PurchaseResult.PENDING)
        val sut = makeSut(service)
        settle()

        sut.events.test {
            sut.onSubscribe()
            settle()
            expectNoEvents()

            service.setSubscribed(true)
            settle()
            assertEquals(PaywallEvent.Subscribed, awaitItem())
        }
    }

    @Test
    fun `onSubscribe when Google Play fails shows its message`() = runTest {
        val service = SwitchableService()
        val sut = makeSut(service)
        settle()

        service.purchaseFailure = SubscriptionError.StoreUnavailable
        sut.onSubscribe()
        settle()

        assertFalse(sut.uiState.value.isPurchasing)
        assertEquals(SubscriptionError.StoreUnavailable.userMessage, sut.uiState.value.error)
    }

    @Test
    fun `while a purchase runs the plan, restore and a second subscribe are ignored`() = runTest {
        val gate = CompletableDeferred<PurchaseResult>()
        val service = SwitchableService(purchaseGate = gate)
        val sut = makeSut(service)
        settle()

        sut.onSubscribe()
        settle()
        sut.onSubscribe()
        sut.onPlanSelected(StoreProductIds.MONTHLY_PLAN)
        sut.onRestore()
        settle()

        assertEquals(1, service.purchaseCalls)
        assertEquals(0, service.restoreCalls)
        assertEquals(StoreProductIds.YEARLY_PLAN, sut.uiState.value.selectedPlanId)
        assertTrue(sut.uiState.value.isPurchasing)

        gate.complete(PurchaseResult.CANCELLED)
        settle()
        assertFalse(sut.uiState.value.isBusy)
    }

    // --- Restoring ---

    @Test
    fun `onRestore without a subscription says there is nothing to restore`() = runTest {
        val service = FakeSubscriptionService()
        val sut = makeSut(service)
        settle()

        sut.onRestore()
        assertTrue(sut.uiState.value.isRestoring)
        assertFalse(sut.uiState.value.canSubscribe)
        settle()

        assertFalse(sut.uiState.value.isRestoring)
        assertEquals(PaywallNotice.NOTHING_TO_RESTORE, sut.uiState.value.notice)
        assertEquals(1, service.restoreCount)
    }

    @Test
    fun `onRestore finding a subscription closes the paywall`() = runTest {
        val service = SwitchableService(restoreFinds = true)
        val sut = makeSut(service)
        settle()

        sut.events.test {
            sut.onRestore()
            settle()
            assertEquals(PaywallEvent.Subscribed, awaitItem())
        }
        assertNull(sut.uiState.value.notice)
    }

    @Test
    fun `onRestore when Google Play fails shows its message and clears an older notice`() = runTest {
        val service = SwitchableService()
        val sut = makeSut(service)
        settle()
        sut.onRestore()
        settle()
        assertEquals(PaywallNotice.NOTHING_TO_RESTORE, sut.uiState.value.notice)

        service.restoreFailure = SubscriptionError.StoreUnavailable
        sut.onRestore()
        settle()

        assertNull(sut.uiState.value.notice)
        assertEquals(SubscriptionError.StoreUnavailable.userMessage, sut.uiState.value.error)
    }

    /**
     * A Fake whose failures can change between calls, whose purchase can be held open, and whose restore can find
     * a subscription (a reinstall on a device that already paid).
     */
    private class SwitchableService(
        var optionsFailure: SubscriptionError? = null,
        var purchaseFailure: SubscriptionError? = null,
        var restoreFailure: SubscriptionError? = null,
        private val purchaseGate: CompletableDeferred<PurchaseResult>? = null,
        private val restoreFinds: Boolean = false,
        private val fake: FakeSubscriptionService = FakeSubscriptionService(latency = Duration.ZERO),
    ) : SubscriptionService by fake {

        var purchaseCalls = 0
            private set
        var restoreCalls = 0
            private set

        override suspend fun subscriptionOptions() = optionsFailure?.let { throw it } ?: fake.subscriptionOptions()

        override suspend fun purchase(option: SubscriptionOption): PurchaseResult {
            purchaseCalls++
            purchaseFailure?.let { throw it }
            return purchaseGate?.await() ?: fake.purchase(option)
        }

        override suspend fun restore() {
            restoreCalls++
            restoreFailure?.let { throw it }
            if (restoreFinds) fake.setSubscribed(true)
        }
    }
}
