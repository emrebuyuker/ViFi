package com.salticusteam.vifi.core.data.billing

import com.salticusteam.vifi.core.model.StoreProductIds
import com.salticusteam.vifi.core.model.SubscriptionError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * In-memory Google Play for the billing services' tests: scripted offers, purchases and purchase-sheet answers.
 * Every acknowledge, consume and launch is appended to [calls], which the other fakes can share to check ordering.
 */
class FakePlayBillingClient(
    val calls: MutableList<String> = mutableListOf(),
) : PlayBillingClient {

    private val updates = MutableSharedFlow<PlayPurchaseUpdate>(extraBufferCapacity = 16)
    override val purchaseUpdates: Flow<PlayPurchaseUpdate> = updates

    /** Offers by product id. */
    var offers: Map<String, List<PlayOffer>> = mapOf(
        StoreProductIds.PREMIUM to listOf(MONTHLY_OFFER, YEARLY_OFFER),
        StoreProductIds.EXAM_UNLOCK to listOf(EXAM_UNLOCK_OFFER),
    )

    /** The device's current purchases by type; consuming removes one. */
    val purchases = mutableMapOf<PlayProductType, List<PlayPurchase>>()

    /** Every query fails with this response. */
    var queryFailure: PlayBillingResponse? = null

    /** The purchase sheet fails to open with this response. */
    var launchFailure: PlayBillingResponse? = null

    /** Play's answer once the sheet opens; `null` leaves it to the test ([emitUpdate]). */
    var sheetAnswer: PlayPurchaseUpdate? = null

    var acknowledgeFailure: PlayBillingResponse? = null
    var consumeFailure: PlayBillingResponse? = null

    val launched = mutableListOf<PlayPurchaseRequest>()
    var purchaseQueries = 0
        private set

    override suspend fun queryOffers(productId: String, type: PlayProductType): List<PlayOffer> {
        queryFailure?.let { throw PlayBillingException(it) }
        return offers[productId].orEmpty()
    }

    override suspend fun queryPurchases(type: PlayProductType): List<PlayPurchase> {
        purchaseQueries++
        queryFailure?.let { throw PlayBillingException(it) }
        return purchases[type].orEmpty()
    }

    override suspend fun launchPurchase(request: PlayPurchaseRequest) {
        launchFailure?.let { throw PlayBillingException(it) }
        launched += request
        calls += "launch"
        sheetAnswer?.let { updates.emit(it) }
    }

    override suspend fun acknowledge(purchaseToken: String) {
        calls += "acknowledge:$purchaseToken"
        acknowledgeFailure?.let { throw PlayBillingException(it) }
        purchases.replaceAll { _, list ->
            list.map { if (it.purchaseToken == purchaseToken) it.copy(isAcknowledged = true) else it }
        }
    }

    override suspend fun consume(purchaseToken: String) {
        calls += "consume:$purchaseToken"
        consumeFailure?.let { throw PlayBillingException(it) }
        purchases.replaceAll { _, list -> list.filterNot { it.purchaseToken == purchaseToken } }
    }

    /** Reports a purchase update, like a payment completing outside the purchase sheet. */
    suspend fun emitUpdate(update: PlayPurchaseUpdate) = updates.emit(update)

    companion object {
        val MONTHLY_OFFER = PlayOffer("monthly", offerId = null, offerToken = "token-monthly", "₺59,99")
        val YEARLY_OFFER = PlayOffer("yearly", offerId = null, offerToken = "token-yearly", "₺399,99")
        val EXAM_UNLOCK_OFFER = PlayOffer(null, offerId = null, offerToken = "token-exam", "₺29,99")

        fun premium(
            token: String,
            state: PlayPurchaseState = PlayPurchaseState.PURCHASED,
            acknowledged: Boolean = false,
        ) = PlayPurchase(listOf(StoreProductIds.PREMIUM), token, state, acknowledged)

        fun examUnlock(token: String, examId: String?, state: PlayPurchaseState = PlayPurchaseState.PURCHASED) =
            PlayPurchase(listOf(StoreProductIds.EXAM_UNLOCK), token, state, false, obfuscatedProfileId = examId)
    }
}

/** Records each paid unlock sent to the server in the shared [calls] log, or fails with [failure]. */
class FakeExamPurchaseBackend(private val calls: MutableList<String>) : ExamPurchaseBackend {

    var failure: SubscriptionError? = null

    override suspend fun recordPurchase(purchaseToken: String, productId: String, examId: String) {
        calls += "record:$purchaseToken:$productId:$examId"
        failure?.let { throw it }
    }
}

class FakePendingExamUnlockStore(var stored: String? = null) : PendingExamUnlockStore {

    override suspend fun examId(): String? = stored

    override suspend fun save(examId: String) {
        stored = examId
    }

    override suspend fun clear(examId: String) {
        if (stored == examId) stored = null
    }
}

class FakeAppForegroundEvents : AppForegroundEvents {

    private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val foregrounds: Flow<Unit> = events

    suspend fun comeToForeground() = events.emit(Unit)
}
