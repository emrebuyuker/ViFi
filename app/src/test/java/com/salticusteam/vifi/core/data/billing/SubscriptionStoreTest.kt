package com.salticusteam.vifi.core.data.billing

import com.salticusteam.vifi.core.data.repository.SubscriptionService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubscriptionService
import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.SubscriptionError
import com.salticusteam.vifi.core.model.SubscriptionOption
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionStoreTest {

    private val yearly = FakeSubscriptionService.SAMPLE_OPTIONS.last()

    @Test
    fun `isSubscribed follows the service from creation`() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeSubscriptionService(subscribed = true, latency = Duration.ZERO)
        val store = SubscriptionStore(service, backgroundScope)

        assertTrue(store.isSubscribed.value)
        service.setSubscribed(false)
        assertFalse(store.isSubscribed.value)
    }

    @Test
    fun `isSubscribed when the status read fails stays locked`() = runTest(UnconfinedTestDispatcher()) {
        val store = SubscriptionStore(FailingStatusService(), backgroundScope)

        assertFalse(store.isSubscribed.value)
    }

    @Test
    fun `loadOptions fills the plans and a failure keeps them`() = runTest(UnconfinedTestDispatcher()) {
        val store = SubscriptionStore(FakeSubscriptionService(latency = Duration.ZERO), backgroundScope)
        store.loadOptions()
        assertEquals(FakeSubscriptionService.SAMPLE_OPTIONS, store.options.value)

        val failing = SubscriptionStore(
            FakeSubscriptionService(latency = Duration.ZERO, failure = SubscriptionError.StoreUnavailable),
            backgroundScope,
        )
        assertEquals(SubscriptionError.StoreUnavailable, runCatching { failing.loadOptions() }.exceptionOrNull())
        assertTrue(failing.options.value.isEmpty())
    }

    @Test
    fun `purchase when successful unlocks at once and when pending stays locked`() = runTest(UnconfinedTestDispatcher()) {
        val store = SubscriptionStore(FakeSubscriptionService(latency = Duration.ZERO), backgroundScope)
        assertEquals(PurchaseResult.SUCCESS, store.purchase(yearly))
        assertTrue(store.isSubscribed.value)

        val pending = SubscriptionStore(
            FakeSubscriptionService(latency = Duration.ZERO, purchaseResult = PurchaseResult.PENDING),
            backgroundScope,
        )
        assertEquals(PurchaseResult.PENDING, pending.purchase(yearly))
        assertFalse(pending.isSubscribed.value)
    }

    @Test
    fun `restore re-reads the entitlement`() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeSubscriptionService(latency = Duration.ZERO)
        val store = SubscriptionStore(service, backgroundScope)

        store.restore()

        assertEquals(1, service.restoreCount)
        assertFalse(store.isSubscribed.value)
    }

    /** A service whose status stream fails before its first value. */
    private class FailingStatusService : SubscriptionService {
        override suspend fun subscriptionOptions(): List<SubscriptionOption> = emptyList()
        override suspend fun isSubscribed(): Boolean = false
        override fun statusChanges(): Flow<Boolean> = flow { throw SubscriptionError.StoreUnavailable }
        override suspend fun purchase(option: SubscriptionOption): PurchaseResult = PurchaseResult.CANCELLED
        override suspend fun restore() = Unit
    }
}
