package com.salticusteam.vifi.core.data.billing

import com.salticusteam.vifi.core.model.SubscriptionError
import com.salticusteam.vifi.core.util.LogCategory

/**
 * Maps a Google Play Billing failure to the error the UI shows (iOS maps StoreKit errors the same way): Play out of
 * reach (no connection, Play Store missing, old or blocked) → [SubscriptionError.StoreUnavailable]; a product Play
 * does not sell → [SubscriptionError.ProductUnavailable]; anything else is logged and becomes
 * [SubscriptionError.Unknown]. Never called with OK or a cancel, which are not errors.
 */
fun PlayBillingResponse.toSubscriptionError(): SubscriptionError = when (this) {
    PlayBillingResponse.BILLING_UNAVAILABLE,
    PlayBillingResponse.FEATURE_NOT_SUPPORTED,
    PlayBillingResponse.SERVICE_UNAVAILABLE,
    PlayBillingResponse.SERVICE_DISCONNECTED,
    PlayBillingResponse.SERVICE_TIMEOUT,
    PlayBillingResponse.NETWORK_ERROR,
    -> SubscriptionError.StoreUnavailable

    PlayBillingResponse.ITEM_UNAVAILABLE -> SubscriptionError.ProductUnavailable

    PlayBillingResponse.OK,
    PlayBillingResponse.USER_CANCELED,
    PlayBillingResponse.ITEM_ALREADY_OWNED,
    PlayBillingResponse.ITEM_NOT_OWNED,
    PlayBillingResponse.DEVELOPER_ERROR,
    PlayBillingResponse.ERROR,
    -> {
        LogCategory.APP.log.e("Google Play Billing failed: %s", this)
        SubscriptionError.Unknown
    }
}

/** Runs a [PlayBillingClient] call, rethrowing its [PlayBillingException] as a [SubscriptionError]. */
internal inline fun <T> billingCall(block: () -> T): T =
    try {
        block()
    } catch (e: PlayBillingException) {
        throw e.response.toSubscriptionError()
    }
