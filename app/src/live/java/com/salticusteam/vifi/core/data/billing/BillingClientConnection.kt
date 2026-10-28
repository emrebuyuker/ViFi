package com.salticusteam.vifi.core.data.billing

import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.salticusteam.vifi.core.util.ForegroundActivityHolder
import com.salticusteam.vifi.core.util.LogCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The app's one Google Play `BillingClient` (4.0.0 BIL-02), created once and never closed.
 *
 * Each call connects first, reconnecting after Play drops the service; a transient failure (disconnected, service
 * unavailable, network, generic error) is retried with exponential backoff before it is thrown. Callers share one
 * connection attempt. Billing's callbacks are awaited, never blocked on; the purchase sheet opens over the Activity
 * held by [ForegroundActivityHolder], so no caller passes one. Pending one-time purchases are enabled (a cash
 * payment completes later). Purchase updates are relayed on [purchaseUpdates].
 */
@Singleton
class BillingClientConnection internal constructor(
    clientFactory: (PurchasesUpdatedListener) -> BillingClient,
    private val activityHolder: ForegroundActivityHolder,
    private val initialBackoff: Duration = 1.seconds,
) : PlayBillingClient {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        activityHolder: ForegroundActivityHolder,
    ) : this(
        clientFactory = { listener ->
            BillingClient.newBuilder(context)
                .setListener(listener)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build()
        },
        activityHolder = activityHolder,
    )

    // State
    // Buffered so Play's main-thread callback never waits; nothing is replayed to a later collector.
    private val _purchaseUpdates = MutableSharedFlow<PlayPurchaseUpdate>(extraBufferCapacity = UPDATE_BUFFER)
    override val purchaseUpdates: Flow<PlayPurchaseUpdate> = _purchaseUpdates.asSharedFlow()

    // Internal state
    private val client: BillingClient = clientFactory(PurchasesUpdatedListener(::onPurchasesUpdated))
    private val connectionLock = Any()
    private var connecting: CompletableDeferred<Int>? = null // guarded by connectionLock

    // The product details a purchase needs, from the last query of each product.
    private val productDetails = ConcurrentHashMap<String, ProductDetails>()

    // Intents
    override suspend fun queryOffers(productId: String, type: PlayProductType): List<PlayOffer> {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(type.billingType)
                        .build(),
                ),
            )
            .build()
        val details = call { billing ->
            suspendCancellableCoroutine { continuation ->
                billing.queryProductDetailsAsync(params) { result, details ->
                    continuation.resume(result to details?.productDetailsList.orEmpty())
                }
            }
        }.firstOrNull { it.productId == productId } ?: return emptyList() // not sold (yet) in Play Console
        productDetails[productId] = details
        return details.offers(type)
    }

    override suspend fun queryPurchases(type: PlayProductType): List<PlayPurchase> {
        val params = QueryPurchasesParams.newBuilder().setProductType(type.billingType).build()
        return call { billing ->
            suspendCancellableCoroutine { continuation ->
                billing.queryPurchasesAsync(params) { result, purchases ->
                    continuation.resume(result to purchases.orEmpty().map { it.toPlayPurchase() })
                }
            }
        }
    }

    override suspend fun launchPurchase(request: PlayPurchaseRequest) {
        val details = productDetails[request.productId]
            ?: queryOffers(request.productId, request.type).let { productDetails[request.productId] }
            ?: throw PlayBillingException(PlayBillingResponse.ITEM_UNAVAILABLE)
        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { if (request.offerToken.isNotBlank()) setOfferToken(request.offerToken) }
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(product))
            .apply {
                request.obfuscatedAccountId?.let(::setObfuscatedAccountId)
                request.obfuscatedProfileId?.let(::setObfuscatedProfileId)
            }
            .build()
        call { billing ->
            // The sheet opens from the main thread, over the app's Activity.
            withContext(Dispatchers.Main.immediate) {
                val activity = activityHolder.activity?.takeUnless { it.isFinishing || it.isDestroyed }
                if (activity == null) {
                    LogCategory.APP.log.e("No Activity to show the Google Play purchase sheet over")
                    throw PlayBillingException(PlayBillingResponse.ERROR)
                }
                billing.launchBillingFlow(activity, params) to Unit
            }
        }
    }

    override suspend fun acknowledge(purchaseToken: String) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()
        call { billing ->
            suspendCancellableCoroutine { continuation ->
                billing.acknowledgePurchase(params) { result -> continuation.resume(result to Unit) }
            }
        }
    }

    override suspend fun consume(purchaseToken: String) {
        val params = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
        call { billing ->
            suspendCancellableCoroutine { continuation ->
                billing.consumeAsync(params) { result, _ -> continuation.resume(result to Unit) }
            }
        }
    }

    // Helpers

    /**
     * Connects, runs [operation] and returns its value when Play answers OK. A transient failure of either step is
     * retried after 1, then 2 seconds (with the default backoff); any other failure, or the last one, is thrown.
     */
    private suspend fun <T> call(operation: suspend (BillingClient) -> Pair<BillingResult, T>): T {
        var backoff = initialBackoff
        for (attempt in 1..MAX_ATTEMPTS) {
            val setupCode = connect()
            val code = if (setupCode == BillingResponseCode.OK) {
                val (result, value) = operation(client)
                if (result.responseCode == BillingResponseCode.OK) return value
                result.responseCode
            } else {
                setupCode
            }
            val response = billingResponse(code)
            if (!response.isTransient || attempt == MAX_ATTEMPTS) throw PlayBillingException(response)
            LogCategory.APP.log.w("Google Play Billing answered %s; retrying in %s", response, backoff)
            delay(backoff)
            backoff *= 2
        }
        error("unreachable")
    }

    /**
     * Returns OK when the client is connected, otherwise starts (or joins) one connection attempt and returns its
     * response code. A dropped service reports SERVICE_DISCONNECTED, and the next call connects again.
     */
    private suspend fun connect(): Int {
        val attempt = synchronized(connectionLock) {
            if (client.isReady) return BillingResponseCode.OK
            connecting ?: CompletableDeferred<Int>().also { deferred ->
                connecting = deferred
                client.startConnection(
                    object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) =
                            finish(deferred, result.responseCode)

                        override fun onBillingServiceDisconnected() =
                            finish(deferred, BillingResponseCode.SERVICE_DISCONNECTED)
                    },
                )
            }
        }
        return attempt.await()
    }

    /** Ends a connection attempt; a later disconnect of the same attempt is ignored (the deferred stays complete). */
    private fun finish(attempt: CompletableDeferred<Int>, code: Int) {
        synchronized(connectionLock) {
            if (connecting === attempt) connecting = null
        }
        attempt.complete(code)
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        _purchaseUpdates.tryEmit(
            PlayPurchaseUpdate(billingResponse(result.responseCode), purchases.orEmpty().map { it.toPlayPurchase() }),
        )
    }

    internal companion object {
        private const val MAX_ATTEMPTS = 3
        private const val UPDATE_BUFFER = 16

        /** Billing's response code as a [PlayBillingResponse]; an unknown code is a generic error. */
        @Suppress("DEPRECATION") // SERVICE_TIMEOUT is deprecated but may still be reported.
        fun billingResponse(code: Int): PlayBillingResponse = when (code) {
            BillingResponseCode.OK -> PlayBillingResponse.OK
            BillingResponseCode.USER_CANCELED -> PlayBillingResponse.USER_CANCELED
            BillingResponseCode.ITEM_ALREADY_OWNED -> PlayBillingResponse.ITEM_ALREADY_OWNED
            BillingResponseCode.ITEM_NOT_OWNED -> PlayBillingResponse.ITEM_NOT_OWNED
            BillingResponseCode.ITEM_UNAVAILABLE -> PlayBillingResponse.ITEM_UNAVAILABLE
            BillingResponseCode.BILLING_UNAVAILABLE -> PlayBillingResponse.BILLING_UNAVAILABLE
            BillingResponseCode.FEATURE_NOT_SUPPORTED -> PlayBillingResponse.FEATURE_NOT_SUPPORTED
            BillingResponseCode.SERVICE_UNAVAILABLE -> PlayBillingResponse.SERVICE_UNAVAILABLE
            BillingResponseCode.SERVICE_DISCONNECTED -> PlayBillingResponse.SERVICE_DISCONNECTED
            BillingResponseCode.SERVICE_TIMEOUT -> PlayBillingResponse.SERVICE_TIMEOUT
            BillingResponseCode.NETWORK_ERROR -> PlayBillingResponse.NETWORK_ERROR
            BillingResponseCode.DEVELOPER_ERROR -> PlayBillingResponse.DEVELOPER_ERROR
            else -> PlayBillingResponse.ERROR
        }

        private val PlayProductType.billingType: String
            get() = when (this) {
                PlayProductType.SUBSCRIPTION -> BillingClient.ProductType.SUBS
                PlayProductType.ONE_TIME -> BillingClient.ProductType.INAPP
            }

        /** A subscription's offers priced at their recurring phase, or a one-time product's buy options. */
        private fun ProductDetails.offers(type: PlayProductType): List<PlayOffer> = when (type) {
            PlayProductType.SUBSCRIPTION -> subscriptionOfferDetails.orEmpty().map { offer ->
                PlayOffer(
                    basePlanId = offer.basePlanId,
                    offerId = offer.offerId,
                    offerToken = offer.offerToken,
                    formattedPrice = offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice.orEmpty(),
                )
            }
            PlayProductType.ONE_TIME -> (oneTimePurchaseOfferDetailsList ?: listOfNotNull(oneTimePurchaseOfferDetails))
                .map { offer ->
                    PlayOffer(
                        basePlanId = null,
                        offerId = offer.offerId,
                        offerToken = offer.offerToken.orEmpty(),
                        formattedPrice = offer.formattedPrice,
                    )
                }
        }

        private fun Purchase.toPlayPurchase() = PlayPurchase(
            productIds = products,
            purchaseToken = purchaseToken,
            state = when (purchaseState) {
                Purchase.PurchaseState.PURCHASED -> PlayPurchaseState.PURCHASED
                Purchase.PurchaseState.PENDING -> PlayPurchaseState.PENDING
                else -> PlayPurchaseState.UNSPECIFIED
            },
            isAcknowledged = isAcknowledged,
            obfuscatedAccountId = accountIdentifiers?.obfuscatedAccountId,
            obfuscatedProfileId = accountIdentifiers?.obfuscatedProfileId,
        )
    }
}
