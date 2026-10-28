package com.salticusteam.vifi.core.data.billing

import com.salticusteam.vifi.core.data.repository.SubscriptionService
import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.StoreProductIds
import com.salticusteam.vifi.core.model.SubscriptionError
import com.salticusteam.vifi.core.model.SubscriptionOption
import com.salticusteam.vifi.core.model.SubscriptionPeriod
import com.salticusteam.vifi.core.util.LogCategory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * ViFi Premium on Google Play Billing, verified on the device (iOS StoreKitSubscriptionService, 4.0.0 DEC-03).
 *
 * The entitlement is a purchased `vifi_premium` among this Google account's current Play purchases; reading it
 * acknowledges one not yet acknowledged (Google refunds it after three days otherwise). Play does not push expiry,
 * refunds or a purchase made on another device, so [statusChanges] reads again at once, on every purchase update and
 * whenever the app returns to the foreground. A failed read keeps the last known state (locked before the first).
 */
@Singleton
class PlayBillingSubscriptionService @Inject constructor(
    private val billing: PlayBillingClient,
    private val foregroundEvents: AppForegroundEvents,
) : SubscriptionService {

    // Internal state
    @Volatile
    private var lastKnown = false

    // Intents
    override suspend fun subscriptionOptions(): List<SubscriptionOption> {
        val offers = billingCall { billing.queryOffers(StoreProductIds.PREMIUM, PlayProductType.SUBSCRIPTION) }
        return subscriptionOptions(offers).ifEmpty { throw SubscriptionError.ProductUnavailable }
    }

    override suspend fun isSubscribed(): Boolean =
        try {
            readEntitlement()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.APP.log.w(e, "Could not read the subscription; keeping the last known state")
            lastKnown
        }

    // Never fails: a failed read re-emits the last known state, so the app-wide observer keeps following.
    override fun statusChanges(): Flow<Boolean> =
        merge(flowOf(Unit), billing.purchaseUpdates.map { }, foregroundEvents.foregrounds)
            .conflate()
            .map { isSubscribed() }
            .distinctUntilChanged()

    override suspend fun purchase(option: SubscriptionOption): PurchaseResult {
        val update = billing.purchaseAndAwait(
            PlayPurchaseRequest(StoreProductIds.PREMIUM, PlayProductType.SUBSCRIPTION, option.offerToken),
        )
        return when (update.response) {
            PlayBillingResponse.OK -> {
                val premium = update.purchases.filter { StoreProductIds.PREMIUM in it.productIds }
                val purchased = premium.filter { it.state == PlayPurchaseState.PURCHASED }
                if (purchased.isEmpty()) return PurchaseResult.PENDING
                purchased.filterNot { it.isAcknowledged }.forEach { acknowledge(it) }
                lastKnown = true
                PurchaseResult.SUCCESS
            }
            PlayBillingResponse.USER_CANCELED -> PurchaseResult.CANCELLED
            // This Google account already holds the subscription (e.g. bought on another device): nothing to buy.
            PlayBillingResponse.ITEM_ALREADY_OWNED ->
                if (readEntitlement()) PurchaseResult.SUCCESS else throw SubscriptionError.Unknown
            else -> throw update.response.toSubscriptionError()
        }
    }

    override suspend fun restore() {
        readEntitlement()
    }

    // Helpers

    /**
     * Reads the entitlement from Google Play, acknowledging what still needs it.
     *
     * @throws SubscriptionError when Google Play cannot be reached.
     */
    private suspend fun readEntitlement(): Boolean {
        val active = billingCall { billing.queryPurchases(PlayProductType.SUBSCRIPTION) }
            .filter { StoreProductIds.PREMIUM in it.productIds && it.state == PlayPurchaseState.PURCHASED }
        active.filterNot { it.isAcknowledged }.forEach { acknowledge(it) }
        lastKnown = active.isNotEmpty()
        return lastKnown
    }

    /** The subscription stays granted if this fails: the next read retries, well before Google's three-day refund. */
    private suspend fun acknowledge(purchase: PlayPurchase) {
        try {
            billing.acknowledge(purchase.purchaseToken)
        } catch (e: PlayBillingException) {
            LogCategory.APP.log.w(e, "Acknowledging the subscription failed; retrying on the next read")
        }
    }

    internal companion object {
        /** The base plans as paywall options, monthly first; other offers and unknown plans are skipped. */
        fun subscriptionOptions(offers: List<PlayOffer>): List<SubscriptionOption> = offers
            .filter { it.offerId == null }
            .mapNotNull { offer ->
                val basePlanId = offer.basePlanId ?: return@mapNotNull null
                val period = SubscriptionPeriod.forBasePlanId(basePlanId) ?: return@mapNotNull null
                SubscriptionOption(basePlanId, offer.offerToken, offer.formattedPrice, period)
            }
            .distinctBy { it.basePlanId }
            .sortedBy { it.period }
    }
}
