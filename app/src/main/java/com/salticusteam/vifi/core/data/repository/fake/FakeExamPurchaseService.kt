package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.ExamPurchaseService
import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.SubscriptionError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * In-memory paid exam unlocks for the mock flavor, previews and tests (iOS MockExamPurchaseService): no Google Play
 * and no server. A [PurchaseResult.SUCCESS] purchase records the exam; [pendingRecovery] is handed out once by
 * [recoverPendingUnlock], like a payment that went through after the app was closed.
 */
class FakeExamPurchaseService(
    private val latency: Duration = 300.milliseconds,
    private val failure: SubscriptionError? = null,
    private val purchaseResult: PurchaseResult = PurchaseResult.SUCCESS,
    private var pendingRecovery: String? = null,
) : ExamPurchaseService {

    private val _purchasedExamIds = mutableListOf<String>()

    /** The exams unlocked with money so far, in order. */
    val purchasedExamIds: List<String> get() = _purchasedExamIds

    override suspend fun purchaseUnlock(examId: String): PurchaseResult {
        delay(latency)
        failure?.let { throw it }
        if (purchaseResult == PurchaseResult.SUCCESS) _purchasedExamIds += examId
        return purchaseResult
    }

    override suspend fun recoverPendingUnlock(): String? {
        val examId = pendingRecovery ?: return null
        pendingRecovery = null
        _purchasedExamIds += examId
        return examId
    }
}
