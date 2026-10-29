package com.salticusteam.vifi.core.data.billing

import com.salticusteam.vifi.core.data.repository.AuthService
import com.salticusteam.vifi.core.data.repository.ExamPurchaseService
import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.StoreProductIds
import com.salticusteam.vifi.core.model.SubscriptionError
import com.salticusteam.vifi.core.util.LogCategory
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Paid single-exam unlocks (`exam_unlock`) on Google Play Billing, recorded server-side (iOS
 * StoreKitExamPurchaseService, 4.0.0 DEC-04).
 *
 * The purchase is bound to its exam with `obfuscatedProfileId` = [examBinding] (exam ids are longer than Play's
 * 64-character limit, their digest is not), which the server checks against Google Play, and to the hashed user id
 * with `obfuscatedAccountId` (Play's fraud signal). The digest cannot be turned back into an exam id, so the exam is
 * kept on the device until the purchase completes, and a recovered purchase is recorded only for an exam the device
 * knows whose binding it carries. The purchase is consumed only after the server has recorded the unlock, so a failed
 * call never loses a paid unlock: it stays unconsumed and [recoverPendingUnlock] finishes it later (one left
 * unconsumed for three days is refunded by Google). An unconsumed `exam_unlock` blocks buying another, so
 * [purchaseUnlock] finishes it first.
 */
@Singleton
class PlayBillingExamPurchaseService @Inject constructor(
    private val billing: PlayBillingClient,
    private val backend: ExamPurchaseBackend,
    private val pendingUnlock: PendingExamUnlockStore,
    private val authService: AuthService,
) : ExamPurchaseService {

    // Internal state
    // A purchase and a recovery never finish the same Play purchase at once.
    private val mutex = Mutex()

    // Intents
    override suspend fun purchaseUnlock(examId: String): PurchaseResult = mutex.withLock {
        // The server records the unlock for the signed-in user: never take a payment it could not record.
        val uid = authService.currentUser?.id ?: run {
            LogCategory.APP.log.e("A paid exam unlock needs a signed-in user")
            throw SubscriptionError.Unknown
        }
        // A paid unlock still waiting for the server blocks buying exam_unlock again: finish it first.
        val owned = finishOwnedPurchases(requestedExamId = examId)
        when {
            examId in owned.recorded -> return PurchaseResult.SUCCESS
            owned.failure != null -> throw owned.failure
            owned.hasPending -> return PurchaseResult.PENDING
        }

        val offer = billingCall { billing.queryOffers(StoreProductIds.EXAM_UNLOCK, PlayProductType.ONE_TIME) }
            .firstOrNull() ?: throw SubscriptionError.ProductUnavailable
        // Remember the exam before paying, for a purchase that completes after this call has returned.
        pendingUnlock.save(examId)
        val update = billing.purchaseAndAwait(
            PlayPurchaseRequest(
                productId = StoreProductIds.EXAM_UNLOCK,
                type = PlayProductType.ONE_TIME,
                offerToken = offer.offerToken,
                obfuscatedAccountId = obfuscatedAccountId(uid),
                obfuscatedProfileId = examBinding(examId),
            ),
        )
        when (update.response) {
            PlayBillingResponse.OK -> {
                val purchase = update.purchases.first { StoreProductIds.EXAM_UNLOCK in it.productIds }
                // Not paid yet: the exam stays remembered and recoverPendingUnlock finishes it once paid.
                if (purchase.state != PlayPurchaseState.PURCHASED) return PurchaseResult.PENDING
                complete(purchase, examId)
                PurchaseResult.SUCCESS
            }
            PlayBillingResponse.USER_CANCELED -> {
                pendingUnlock.clear(examId)
                PurchaseResult.CANCELLED
            }
            else -> {
                pendingUnlock.clear(examId)
                throw update.response.toSubscriptionError()
            }
        }
    }

    override suspend fun recoverPendingUnlock(): String? = mutex.withLock {
        // Recorded for the signed-in user only; a signed-out launch leaves the purchase for the next sign-in.
        if (authService.currentUser == null) return@withLock null
        try {
            val owned = finishOwnedPurchases()
            owned.failure?.let { LogCategory.APP.log.w(it, "Recovering a paid exam unlock failed; retrying next time") }
            owned.recorded.firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.APP.log.w(e, "Could not look for a paid exam unlock to recover")
            null
        }
    }

    // Helpers

    /**
     * Finishes this Google account's unconsumed `exam_unlock` purchases: each paid one is recorded for the exam it is
     * bound to, then consumed. The exam is found among those this device knows ([requestedExamId] and the remembered
     * one); a purchase bound to none of them is left alone, and Google refunds it if it is never consumed. A failure is
     * reported after trying the others.
     *
     * @throws SubscriptionError when Google Play cannot be reached.
     */
    private suspend fun finishOwnedPurchases(requestedExamId: String? = null): OwnedUnlocks {
        val owned = billingCall { billing.queryPurchases(PlayProductType.ONE_TIME) }
            .filter { StoreProductIds.EXAM_UNLOCK in it.productIds }
        val rememberedExamId = pendingUnlock.examId()
        if (owned.isEmpty()) {
            // Nothing paid is waiting, so a leftover record (the app died before clearing it) is stale.
            rememberedExamId?.let { pendingUnlock.clear(it) }
            return OwnedUnlocks()
        }

        val knownExamIds = listOfNotNull(requestedExamId, rememberedExamId).distinct()
        val recorded = mutableListOf<String>()
        var failure: SubscriptionError? = null
        for (purchase in owned.filter { it.state == PlayPurchaseState.PURCHASED }) {
            val examId = knownExamIds.firstOrNull { examBinding(it) == purchase.obfuscatedProfileId }
            if (examId == null) {
                LogCategory.APP.log.e("A paid exam unlock is bound to no exam this device knows; Google refunds it")
                continue
            }
            try {
                complete(purchase, examId)
                recorded += examId
            } catch (e: SubscriptionError) {
                failure = failure ?: e
            }
        }
        return OwnedUnlocks(recorded, owned.any { it.state == PlayPurchaseState.PENDING }, failure)
    }

    /**
     * Records the unlock on the server, then consumes the purchase — never the other way round, or a failed call
     * would lose a paid unlock.
     *
     * @throws SubscriptionError when the server rejects the purchase or cannot be reached; nothing is consumed then.
     */
    private suspend fun complete(purchase: PlayPurchase, examId: String) {
        backend.recordPurchase(purchase.purchaseToken, StoreProductIds.EXAM_UNLOCK, examId)
        try {
            billing.consume(purchase.purchaseToken)
        } catch (e: PlayBillingException) {
            // The unlock is recorded; the next recovery consumes it (recording again is idempotent).
            LogCategory.APP.log.w(e, "Consuming a recorded exam unlock failed")
        }
        pendingUnlock.clear(examId)
    }

    /** The outcome of [finishOwnedPurchases]: the exams recorded, whether a payment is still pending, a failure. */
    private data class OwnedUnlocks(
        val recorded: List<String> = emptyList(),
        val hasPending: Boolean = false,
        val failure: SubscriptionError? = null,
    )

    internal companion object {
        /** The user id as Google Play may see it: a SHA-256 hex digest (64 characters, Play's limit). */
        fun obfuscatedAccountId(uid: String): String = sha256Hex(uid)

        /**
         * How a purchase names its exam to Google Play: the SHA-256 hex digest of the exam id, 64 characters for any
         * exam (the server's `examBindingFor` computes the same).
         */
        fun examBinding(examId: String): String = sha256Hex(examId)

        private fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
