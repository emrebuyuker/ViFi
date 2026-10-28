package com.salticusteam.vifi.core.data.billing

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.billing.FakePlayBillingClient.Companion.premium
import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.StoreProductIds
import com.salticusteam.vifi.core.model.SubscriptionError
import com.salticusteam.vifi.core.model.SubscriptionOption
import com.salticusteam.vifi.core.model.SubscriptionPeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayBillingSubscriptionServiceTest {

    private val billing = FakePlayBillingClient()
    private val foreground = FakeAppForegroundEvents()
    private val sut = PlayBillingSubscriptionService(billing, foreground)

    private val yearly = SubscriptionOption("yearly", "token-yearly", "₺399,99", SubscriptionPeriod.YEARLY)

    // --- subscriptionOptions ---

    @Test
    fun `subscriptionOptions lists the base plans monthly first`() = runTest {
        billing.offers = mapOf(
            StoreProductIds.PREMIUM to listOf(
                FakePlayBillingClient.YEARLY_OFFER,
                FakePlayBillingClient.YEARLY_OFFER.copy(offerId = "intro", offerToken = "token-intro"),
                PlayOffer("weekly", offerId = null, offerToken = "token-weekly", formattedPrice = "₺19,99"),
                FakePlayBillingClient.MONTHLY_OFFER,
            ),
        )

        assertEquals(
            listOf(
                SubscriptionOption("monthly", "token-monthly", "₺59,99", SubscriptionPeriod.MONTHLY),
                yearly,
            ),
            sut.subscriptionOptions(),
        )
    }

    @Test
    fun `subscriptionOptions when Play sells no plan throws productUnavailable`() = runTest {
        billing.offers = emptyMap()

        assertEquals(SubscriptionError.ProductUnavailable, runCatching { sut.subscriptionOptions() }.exceptionOrNull())
    }

    @Test
    fun `subscriptionOptions when Play is out of reach throws storeUnavailable`() = runTest {
        billing.queryFailure = PlayBillingResponse.BILLING_UNAVAILABLE

        assertEquals(SubscriptionError.StoreUnavailable, runCatching { sut.subscriptionOptions() }.exceptionOrNull())
    }

    // --- isSubscribed ---

    @Test
    fun `isSubscribed counts only a purchased premium and acknowledges it`() = runTest {
        billing.purchases[PlayProductType.SUBSCRIPTION] = listOf(premium("pending", PlayPurchaseState.PENDING))
        assertFalse(sut.isSubscribed())

        billing.purchases[PlayProductType.SUBSCRIPTION] = listOf(premium("t1"), premium("t2", acknowledged = true))
        assertTrue(sut.isSubscribed())
        assertEquals(listOf("acknowledge:t1"), billing.calls)
    }

    @Test
    fun `isSubscribed when acknowledging fails still grants and retries next read`() = runTest {
        billing.purchases[PlayProductType.SUBSCRIPTION] = listOf(premium("t1"))
        billing.acknowledgeFailure = PlayBillingResponse.SERVICE_UNAVAILABLE

        assertTrue(sut.isSubscribed())
        billing.acknowledgeFailure = null
        assertTrue(sut.isSubscribed())
        assertEquals(listOf("acknowledge:t1", "acknowledge:t1"), billing.calls)
    }

    @Test
    fun `isSubscribed when Play fails keeps the last known state`() = runTest {
        billing.queryFailure = PlayBillingResponse.SERVICE_UNAVAILABLE
        assertFalse(sut.isSubscribed())

        billing.queryFailure = null
        billing.purchases[PlayProductType.SUBSCRIPTION] = listOf(premium("t1", acknowledged = true))
        assertTrue(sut.isSubscribed())

        billing.queryFailure = PlayBillingResponse.NETWORK_ERROR
        assertTrue(sut.isSubscribed())
    }

    // --- statusChanges ---

    @Test
    fun `statusChanges reads at once then on purchase updates and foreground returns`() = runTest {
        sut.statusChanges().test {
            assertFalse(awaitItem())

            billing.purchases[PlayProductType.SUBSCRIPTION] = listOf(premium("t1", acknowledged = true))
            billing.emitUpdate(PlayPurchaseUpdate(PlayBillingResponse.OK, listOf(premium("t1", acknowledged = true))))
            assertTrue(awaitItem())

            // Expired while the app was in the background: Play pushes nothing.
            billing.purchases[PlayProductType.SUBSCRIPTION] = emptyList()
            foreground.comeToForeground()
            assertFalse(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `statusChanges when a read fails keeps following`() = runTest {
        billing.queryFailure = PlayBillingResponse.SERVICE_DISCONNECTED

        sut.statusChanges().test {
            assertFalse(awaitItem())

            billing.queryFailure = null
            billing.purchases[PlayProductType.SUBSCRIPTION] = listOf(premium("t1", acknowledged = true))
            foreground.comeToForeground()
            assertTrue(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- purchase ---

    @Test
    fun `purchase when paid acknowledges and is subscribed at once`() = runTest {
        billing.sheetAnswer = PlayPurchaseUpdate(PlayBillingResponse.OK, listOf(premium("t1")))
        billing.queryFailure = PlayBillingResponse.SERVICE_UNAVAILABLE // a flaky re-read must not relock

        assertEquals(PurchaseResult.SUCCESS, sut.purchase(yearly))
        assertEquals(
            PlayPurchaseRequest(StoreProductIds.PREMIUM, PlayProductType.SUBSCRIPTION, "token-yearly"),
            billing.launched.single(),
        )
        assertEquals(listOf("launch", "acknowledge:t1"), billing.calls)
        assertTrue(sut.isSubscribed())
    }

    @Test
    fun `purchase when the payment is pending neither grants nor acknowledges`() = runTest {
        billing.sheetAnswer = PlayPurchaseUpdate(
            PlayBillingResponse.OK,
            listOf(premium("t1", PlayPurchaseState.PENDING)),
        )

        assertEquals(PurchaseResult.PENDING, sut.purchase(yearly))
        assertEquals(listOf("launch"), billing.calls)
    }

    @Test
    fun `purchase when the user cancels returns cancelled`() = runTest {
        billing.sheetAnswer = PlayPurchaseUpdate(PlayBillingResponse.USER_CANCELED)

        assertEquals(PurchaseResult.CANCELLED, sut.purchase(yearly))
    }

    @Test
    fun `purchase when already owned on this account re-reads and succeeds`() = runTest {
        billing.sheetAnswer = PlayPurchaseUpdate(PlayBillingResponse.ITEM_ALREADY_OWNED)
        billing.purchases[PlayProductType.SUBSCRIPTION] = listOf(premium("t1", acknowledged = true))

        assertEquals(PurchaseResult.SUCCESS, sut.purchase(yearly))
    }

    @Test
    fun `purchase when the sheet cannot open maps the error`() = runTest {
        billing.launchFailure = PlayBillingResponse.SERVICE_DISCONNECTED
        assertEquals(SubscriptionError.StoreUnavailable, runCatching { sut.purchase(yearly) }.exceptionOrNull())

        billing.launchFailure = null
        billing.sheetAnswer = PlayPurchaseUpdate(PlayBillingResponse.DEVELOPER_ERROR)
        assertEquals(SubscriptionError.Unknown, runCatching { sut.purchase(yearly) }.exceptionOrNull())
    }

    @Test
    fun `purchase waits for the premium update and ignores other products`() = runTest {
        billing.sheetAnswer = PlayPurchaseUpdate(
            PlayBillingResponse.OK,
            listOf(FakePlayBillingClient.examUnlock("exam-token", "exam-1")),
        )

        val purchase = async { sut.purchase(yearly) }
        runCurrent()
        assertFalse(purchase.isCompleted)

        billing.emitUpdate(PlayPurchaseUpdate(PlayBillingResponse.OK, listOf(premium("t1"))))
        assertEquals(PurchaseResult.SUCCESS, purchase.await())
    }

    // --- restore ---

    @Test
    fun `restore re-reads purchases and throws when Play is out of reach`() = runTest {
        sut.restore()
        assertEquals(1, billing.purchaseQueries)

        billing.queryFailure = PlayBillingResponse.SERVICE_UNAVAILABLE
        assertEquals(SubscriptionError.StoreUnavailable, runCatching { sut.restore() }.exceptionOrNull())
    }
}
