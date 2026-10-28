package com.salticusteam.vifi.core.data.billing

import com.salticusteam.vifi.core.data.repository.ExamPurchaseService
import com.salticusteam.vifi.core.data.repository.SubscriptionService
import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.SubscriptionError
import com.salticusteam.vifi.core.model.SubscriptionOption
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/*
 * Live billing until Google Play Billing lands (4.0.0 BIL-02/BIL-03/BIL-05 replace these): Premium is never active
 * and nothing can be bought, so no live build can grant Premium or a paid unlock without a real, verified payment.
 */

/** Reports no subscription and offers no plans. */
class UnavailableSubscriptionService @Inject constructor() : SubscriptionService {

    override suspend fun subscriptionOptions(): List<SubscriptionOption> = throw SubscriptionError.ProductUnavailable

    override suspend fun isSubscribed(): Boolean = false

    override fun statusChanges(): Flow<Boolean> = flowOf(false)

    override suspend fun purchase(option: SubscriptionOption): PurchaseResult =
        throw SubscriptionError.ProductUnavailable

    override suspend fun restore() = Unit
}

/** Sells no single-exam unlock and has nothing to recover. */
class UnavailableExamPurchaseService @Inject constructor() : ExamPurchaseService {

    override suspend fun purchaseUnlock(examId: String): PurchaseResult = throw SubscriptionError.ProductUnavailable

    override suspend fun recoverPendingUnlock(): String? = null
}
