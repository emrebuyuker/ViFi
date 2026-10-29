package com.salticusteam.vifi.core.data.billing

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * A thin, Billing-type-free view of Google Play Billing: the one `BillingClient` of the live flavor
 * (`BillingClientConnection`) behind plain Kotlin types, so the subscription and exam-purchase services are unit
 * tested with a fake and the mock flavor never links the Billing Library.
 *
 * Every call connects (or reconnects) first and throws [PlayBillingException] when Google Play answers with an error.
 */
interface PlayBillingClient {
    /**
     * Every purchase update Google Play reports: the answer to [launchPurchase], and purchases that complete outside
     * the purchase sheet (a pending payment that went through, a code redeemed in the Play Store).
     */
    val purchaseUpdates: Flow<PlayPurchaseUpdate>

    /** The purchasable offers of [productId] (a subscription's base plans, a one-time product's buy option). */
    suspend fun queryOffers(productId: String, type: PlayProductType): List<PlayOffer>

    /** The device's current purchases of [type]: active subscriptions, unconsumed one-time products. */
    suspend fun queryPurchases(type: PlayProductType): List<PlayPurchase>

    /**
     * Opens the Google Play purchase sheet over the foreground Activity. Play's answer arrives on [purchaseUpdates];
     * use [purchaseAndAwait] to wait for it.
     */
    suspend fun launchPurchase(request: PlayPurchaseRequest)

    /** Acknowledges a subscription purchase; Google refunds one left unacknowledged for three days. */
    suspend fun acknowledge(purchaseToken: String)

    /** Consumes a one-time purchase, so the product can be bought again (it also acknowledges it). */
    suspend fun consume(purchaseToken: String)
}

// Added in 4.0.0 — the two Google Play product types ViFi sells.
enum class PlayProductType {
    /** An auto-renewing subscription (`vifi_premium`). */
    SUBSCRIPTION,

    /** A one-time product (`exam_unlock`, consumed after each purchase). */
    ONE_TIME,
}

// Added in 4.0.0 — a purchasable offer of a Google Play product.
/**
 * [basePlanId] names a subscription's base plan (`null` for a one-time product); [offerId] is `null` for the base
 * offer itself (no trial or promotional offer); [formattedPrice] is the recurring price as Play localizes it.
 */
data class PlayOffer(
    val basePlanId: String?,
    val offerId: String?,
    val offerToken: String,
    val formattedPrice: String,
)

// Added in 4.0.0 — a Google Play purchase, without Billing types.
/**
 * [obfuscatedProfileId] carries the exam id a paid unlock was bought for; [obfuscatedAccountId] the hashed user id.
 * Both are `null` for purchases that did not set them.
 */
data class PlayPurchase(
    val productIds: List<String>,
    val purchaseToken: String,
    val state: PlayPurchaseState,
    val isAcknowledged: Boolean,
    val obfuscatedAccountId: String? = null,
    val obfuscatedProfileId: String? = null,
)

// Added in 4.0.0 — Google Play's purchase states.
enum class PlayPurchaseState {
    /** Paid: grant it (and acknowledge or consume it). */
    PURCHASED,

    /** Awaiting an external payment; grant nothing yet. */
    PENDING,

    /** Unknown to Play; grant nothing. */
    UNSPECIFIED,
}

// Added in 4.0.0 — Google Play Billing's response codes, without Billing types.
enum class PlayBillingResponse {
    OK,
    USER_CANCELED,
    ITEM_ALREADY_OWNED,
    ITEM_NOT_OWNED,
    ITEM_UNAVAILABLE,
    BILLING_UNAVAILABLE,
    FEATURE_NOT_SUPPORTED,
    SERVICE_UNAVAILABLE,
    SERVICE_DISCONNECTED,
    SERVICE_TIMEOUT,
    NETWORK_ERROR,
    DEVELOPER_ERROR,
    ERROR,
    ;

    /** Whether the same call may succeed after a reconnect and a short wait (Google's retry guidance). */
    val isTransient: Boolean
        get() = when (this) {
            SERVICE_DISCONNECTED, SERVICE_UNAVAILABLE, SERVICE_TIMEOUT, NETWORK_ERROR, ERROR -> true
            else -> false
        }
}

/** One purchase update from Google Play: a response and, when [response] is OK, the purchases it concerns. */
data class PlayPurchaseUpdate(
    val response: PlayBillingResponse,
    val purchases: List<PlayPurchase> = emptyList(),
)

/**
 * What to buy: [offerToken] picks the plan or buy option (blank for none); the obfuscated ids bind the purchase to
 * a hashed user and, for a paid unlock, the hashed exam (each at most [MAX_OBFUSCATED_ID_LENGTH] characters).
 */
data class PlayPurchaseRequest(
    val productId: String,
    val type: PlayProductType,
    val offerToken: String,
    val obfuscatedAccountId: String? = null,
    val obfuscatedProfileId: String? = null,
) {
    companion object {
        /** Google Play rejects longer obfuscated account and profile ids. */
        const val MAX_OBFUSCATED_ID_LENGTH = 64
    }
}

/** Google Play answered a call with [response] (never OK); the services map it to a SubscriptionError. */
class PlayBillingException(val response: PlayBillingResponse) : Exception("Google Play Billing: $response")

/**
 * Opens the purchase sheet for [request] and waits for Play's answer about it: an error or cancel, or an update
 * carrying a purchase of the requested product. The update stream is subscribed before the sheet opens, so the
 * answer cannot be missed; a sheet that fails to open is reported as an update with that response.
 */
suspend fun PlayBillingClient.purchaseAndAwait(request: PlayPurchaseRequest): PlayPurchaseUpdate = coroutineScope {
    val answer = async(start = CoroutineStart.UNDISPATCHED) {
        purchaseUpdates.first { update ->
            update.response != PlayBillingResponse.OK ||
                update.purchases.any { request.productId in it.productIds }
        }
    }
    try {
        launchPurchase(request)
    } catch (e: PlayBillingException) {
        answer.cancel()
        return@coroutineScope PlayPurchaseUpdate(e.response)
    } catch (e: Throwable) {
        answer.cancel()
        throw e
    }
    answer.await()
}
