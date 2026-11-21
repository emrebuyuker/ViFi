package com.salticusteam.vifi.core.data.billing

import com.salticusteam.vifi.core.data.billing.FakePlayBillingClient.Companion.premium
import com.salticusteam.vifi.core.model.StoreProductIds
import com.salticusteam.vifi.core.model.SubscriptionProof
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Premium proof sent with every practice call (4.2.0 PRC-03). */
class PlaySubscriptionProofProviderTest {

    private val billing = FakePlayBillingClient()
    private val service = PlayBillingSubscriptionService(billing, FakeAppForegroundEvents())
    private val sut = PlaySubscriptionProofProvider(service)

    @Test
    fun `an active subscription is proved with its purchase token and product`() = runTest {
        billing.purchases[PlayProductType.SUBSCRIPTION] = listOf(premium("token-1", acknowledged = true))

        val proof = sut.currentProof()

        assertEquals(SubscriptionProof("token-1", StoreProductIds.PREMIUM), proof)
        assertEquals(
            mapOf("platform" to "android", "purchaseToken" to "token-1", "productId" to "vifi_premium"),
            proof?.payload(),
        )
    }

    @Test
    fun `no subscription, or one still pending, has no proof`() = runTest {
        assertNull(sut.currentProof())

        billing.purchases[PlayProductType.SUBSCRIPTION] = listOf(premium("pending", PlayPurchaseState.PENDING))
        assertNull(sut.currentProof())
    }

    @Test
    fun `the proof is read again on every call`() = runTest {
        billing.purchases[PlayProductType.SUBSCRIPTION] = listOf(premium("token-1", acknowledged = true))
        assertEquals("token-1", sut.currentProof()?.purchaseToken)

        billing.purchases[PlayProductType.SUBSCRIPTION] = emptyList()
        assertNull(sut.currentProof())

        billing.purchases[PlayProductType.SUBSCRIPTION] = listOf(premium("token-2", acknowledged = true))
        assertEquals("token-2", sut.currentProof()?.purchaseToken)
        assertEquals(3, billing.purchaseQueries)
    }

    @Test
    fun `a Google Play failure means the free rules, not an error`() = runTest {
        billing.queryFailure = PlayBillingResponse.BILLING_UNAVAILABLE

        assertNull(sut.currentProof())
    }

    @Test
    fun `reading the proof acknowledges a subscription that still needs it`() = runTest {
        billing.purchases[PlayProductType.SUBSCRIPTION] = listOf(premium("token-1"))

        assertTrue(sut.currentProof() != null)

        assertEquals(listOf("acknowledge:token-1"), billing.calls)
    }

    @Test
    fun `the proof never prints its token`() {
        val text = SubscriptionProof("secret-token", StoreProductIds.PREMIUM).toString()

        assertFalse(text.contains("secret-token"))
    }
}
