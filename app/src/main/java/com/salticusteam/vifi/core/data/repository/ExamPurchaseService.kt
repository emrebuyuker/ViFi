package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.PurchaseResult

/**
 * Buys a single exam with money (the `exam_unlock` consumable) instead of coins (iOS ExamPurchaseServicing).
 *
 * The unlock is recorded server-side: after Google Play confirms the payment, the purchase is sent to the
 * `purchaseExamUnlock` Cloud Function, which verifies it with Google Play before writing the unlock; only then is
 * the purchase consumed, so a dropped network never loses a paid unlock. A purchase needs the foreground Activity,
 * which the implementation obtains itself. `FakeExamPurchaseService` backs the mock flavor, previews and tests.
 */
interface ExamPurchaseService {
    /**
     * Buys the single-exam unlock and records it for [examId]. [PurchaseResult.SUCCESS] means the unlock is
     * recorded; [PurchaseResult.PENDING] means the payment completes later and is finished by
     * [recoverPendingUnlock].
     *
     * @throws com.salticusteam.vifi.core.model.SubscriptionError when Google Play or the server fails.
     */
    suspend fun purchaseUnlock(examId: String): PurchaseResult

    /**
     * Records a paid unlock whose server call did not complete earlier (a dropped network, or a pending payment
     * that went through later). Called once the user is signed in. Returns the exam id that was unlocked, or
     * `null` when there was nothing to recover (or it failed and will be retried next time).
     */
    suspend fun recoverPendingUnlock(): String?
}
