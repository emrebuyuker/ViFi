package com.salticusteam.vifi.core.data.billing

import com.salticusteam.vifi.core.model.SubscriptionError
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Callable error codes are mapped in the instrumented FirebaseExamPurchaseErrorMappingTest (Code needs Android). */
class PlayBillingErrorsTest {

    @Test
    fun `toSubscriptionError when Play is out of reach maps to storeUnavailable`() {
        listOf(
            PlayBillingResponse.BILLING_UNAVAILABLE,
            PlayBillingResponse.FEATURE_NOT_SUPPORTED,
            PlayBillingResponse.SERVICE_UNAVAILABLE,
            PlayBillingResponse.SERVICE_DISCONNECTED,
            PlayBillingResponse.SERVICE_TIMEOUT,
            PlayBillingResponse.NETWORK_ERROR,
        ).forEach { assertEquals(it.name, SubscriptionError.StoreUnavailable, it.toSubscriptionError()) }
    }

    @Test
    fun `toSubscriptionError maps an unsold product and falls back to unknown`() {
        assertEquals(SubscriptionError.ProductUnavailable, PlayBillingResponse.ITEM_UNAVAILABLE.toSubscriptionError())
        assertEquals(SubscriptionError.Unknown, PlayBillingResponse.DEVELOPER_ERROR.toSubscriptionError())
        assertEquals(SubscriptionError.Unknown, PlayBillingResponse.ERROR.toSubscriptionError())
    }

    @Test
    fun `billingCall rethrows a Play failure as its subscription error`() {
        val error = runCatching { billingCall { throw PlayBillingException(PlayBillingResponse.ITEM_UNAVAILABLE) } }
            .exceptionOrNull()

        assertEquals(SubscriptionError.ProductUnavailable, error)
    }

    @Test
    fun `isTransient covers the responses Google advises retrying`() {
        assertTrue(PlayBillingResponse.SERVICE_DISCONNECTED.isTransient)
        assertTrue(PlayBillingResponse.NETWORK_ERROR.isTransient)
        assertTrue(PlayBillingResponse.ERROR.isTransient)
        assertFalse(PlayBillingResponse.BILLING_UNAVAILABLE.isTransient)
        assertFalse(PlayBillingResponse.USER_CANCELED.isTransient)
        assertFalse(PlayBillingResponse.DEVELOPER_ERROR.isTransient)
    }

    @Test
    fun `recordError when the transport fails maps to storeUnavailable like iOS`() {
        assertEquals(SubscriptionError.StoreUnavailable, FirebaseExamPurchaseBackend.recordError(IOException("reset")))
        assertEquals(SubscriptionError.Unknown, FirebaseExamPurchaseBackend.recordError(IllegalStateException("x")))
    }
}
