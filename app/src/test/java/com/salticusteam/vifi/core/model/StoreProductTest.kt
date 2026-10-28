package com.salticusteam.vifi.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreProductTest {

    @Test
    fun `forBasePlanId when a premium plan maps to its period`() {
        assertEquals(SubscriptionPeriod.MONTHLY, SubscriptionPeriod.forBasePlanId(StoreProductIds.MONTHLY_PLAN))
        assertEquals(SubscriptionPeriod.YEARLY, SubscriptionPeriod.forBasePlanId(StoreProductIds.YEARLY_PLAN))
    }

    @Test
    fun `forBasePlanId when not a premium plan returns null`() {
        assertNull(SubscriptionPeriod.forBasePlanId(StoreProductIds.EXAM_UNLOCK))
        assertNull(SubscriptionPeriod.forBasePlanId(StoreProductIds.PREMIUM))
        assertNull(SubscriptionPeriod.forBasePlanId("weekly"))
    }

    @Test
    fun `product ids when compared match play console and the server`() {
        // Play ids can never be renamed once created (DEC-01); `exam_unlock` is also checked by purchaseExamUnlock.
        assertEquals("vifi_premium", StoreProductIds.PREMIUM)
        assertEquals("monthly", StoreProductIds.MONTHLY_PLAN)
        assertEquals("yearly", StoreProductIds.YEARLY_PLAN)
        assertEquals("exam_unlock", StoreProductIds.EXAM_UNLOCK)
    }

    @Test
    fun `subscription error when each case has a turkish message and a stable reason`() {
        assertEquals(
            "Abonelik şu anda mağazadan alınamıyor. Lütfen daha sonra tekrar dene.",
            SubscriptionError.ProductUnavailable.userMessage,
        )
        assertEquals(
            "Google Play'e ulaşılamıyor. Bağlantını kontrol edip tekrar dene.",
            SubscriptionError.StoreUnavailable.userMessage,
        )
        assertEquals("Satın alma doğrulanamadı. Lütfen tekrar dene.", SubscriptionError.FailedVerification.userMessage)
        assertEquals(GENERIC_ERROR_MESSAGE, SubscriptionError.Unknown.userMessage)
        assertEquals(
            listOf("product_unavailable", "store_unavailable", "failed_verification", "unknown"),
            listOf(
                SubscriptionError.ProductUnavailable,
                SubscriptionError.StoreUnavailable,
                SubscriptionError.FailedVerification,
                SubscriptionError.Unknown,
            ).map { it.analyticsReason },
        )
    }

    @Test
    fun `userMessage when a subscription error is thrown shows its own message`() {
        val thrown: Throwable = SubscriptionError.StoreUnavailable
        assertEquals(SubscriptionError.StoreUnavailable.userMessage, thrown.userMessage)
    }
}
