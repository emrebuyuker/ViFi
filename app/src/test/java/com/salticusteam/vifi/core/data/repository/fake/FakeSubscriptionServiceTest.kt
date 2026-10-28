package com.salticusteam.vifi.core.data.repository.fake

import app.cash.turbine.test
import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.SubscriptionError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSubscriptionServiceTest {

    private val yearly = FakeSubscriptionService.SAMPLE_OPTIONS.last()

    @Test
    fun `statusChanges emits the current state then a successful purchase`() = runTest {
        val sut = FakeSubscriptionService()

        sut.statusChanges().test {
            assertFalse(awaitItem())
            assertEquals(PurchaseResult.SUCCESS, sut.purchase(yearly))
            assertTrue(awaitItem())
        }
        assertTrue(sut.isSubscribed())
        assertEquals(listOf(yearly), sut.purchased)
    }

    @Test
    fun `purchase when pending or cancelled leaves the subscription off`() = runTest {
        for (result in listOf(PurchaseResult.PENDING, PurchaseResult.CANCELLED)) {
            val sut = FakeSubscriptionService(purchaseResult = result)

            assertEquals(result, sut.purchase(yearly))
            assertFalse(sut.isSubscribed())
        }
    }

    @Test
    fun `subscriptionOptions lists monthly first and failure makes calls throw`() = runTest {
        assertEquals(
            listOf("monthly", "yearly"),
            FakeSubscriptionService().subscriptionOptions().map { it.basePlanId },
        )

        val failing = FakeSubscriptionService(failure = SubscriptionError.StoreUnavailable)
        assertEquals(SubscriptionError.StoreUnavailable, runCatching { failing.subscriptionOptions() }.exceptionOrNull())
        assertEquals(SubscriptionError.StoreUnavailable, runCatching { failing.restore() }.exceptionOrNull())
        assertEquals(0, failing.restoreCount)
    }

    @Test
    fun `exam purchase records success and hands a pending recovery out once`() = runTest {
        val sut = FakeExamPurchaseService(pendingRecovery = "e9")

        assertEquals(PurchaseResult.SUCCESS, sut.purchaseUnlock("e1"))
        assertEquals("e9", sut.recoverPendingUnlock())
        assertNull(sut.recoverPendingUnlock())
        assertEquals(listOf("e1", "e9"), sut.purchasedExamIds)

        val pending = FakeExamPurchaseService(purchaseResult = PurchaseResult.PENDING)
        assertEquals(PurchaseResult.PENDING, pending.purchaseUnlock("e2"))
        assertTrue(pending.purchasedExamIds.isEmpty())
    }
}
