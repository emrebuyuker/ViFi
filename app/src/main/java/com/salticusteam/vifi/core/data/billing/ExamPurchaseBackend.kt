package com.salticusteam.vifi.core.data.billing

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.salticusteam.vifi.core.model.SubscriptionError
import com.salticusteam.vifi.core.util.LogCategory
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Records a paid exam unlock server-side, where it is verified with Google Play first (4.0.0 DEC-04). */
interface ExamPurchaseBackend {
    /**
     * Sends a Google Play purchase of [productId] to be verified (paid, the product, bought for [examId]) and the
     * unlock of [examId] recorded for the signed-in user. Idempotent for the same user, exam and purchase.
     *
     * @throws SubscriptionError when the purchase is rejected or the server cannot be reached.
     */
    suspend fun recordPurchase(purchaseToken: String, productId: String, examId: String)
}

/**
 * The shared `purchaseExamUnlock` callable (europe-west1, injected by FirebaseModule), whose Google Play branch checks
 * the purchase with the Play Developer API and writes `purchasedTransactions` + `users/<uid>/unlocks/<examId>` in one
 * transaction (4.0.0 SRV-01). The App Check token is attached to the call automatically.
 */
class FirebaseExamPurchaseBackend @Inject constructor(
    private val functions: FirebaseFunctions,
) : ExamPurchaseBackend {

    override suspend fun recordPurchase(purchaseToken: String, productId: String, examId: String) {
        try {
            functions.getHttpsCallable(FUNCTION_NAME)
                .call(
                    mapOf(
                        "platform" to PLATFORM,
                        "purchaseToken" to purchaseToken,
                        "productId" to productId,
                        "examId" to examId,
                    ),
                )
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw recordError(e)
        }
    }

    internal companion object {
        private const val FUNCTION_NAME = "purchaseExamUnlock"
        private const val PLATFORM = "android"

        /** Maps a callable failure to a domain error (iOS `StoreKitExamPurchaseService.recordError(from:)`). */
        fun recordError(error: Throwable): SubscriptionError {
            val mapped = when (error) {
                is FirebaseFunctionsException -> recordError(error.code)
                is IOException -> SubscriptionError.StoreUnavailable
                else -> null
            }
            if (mapped != null) return mapped
            LogCategory.APP.log.e(error, "Recording a paid exam unlock failed")
            return SubscriptionError.Unknown
        }

        /** A rejected purchase is a failed verification (the server answers invalid-argument for a malformed one). */
        fun recordError(code: FirebaseFunctionsException.Code): SubscriptionError? = when (code) {
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            FirebaseFunctionsException.Code.INVALID_ARGUMENT -> SubscriptionError.FailedVerification
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> SubscriptionError.StoreUnavailable
            else -> null
        }
    }
}
