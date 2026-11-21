package com.salticusteam.vifi.core.data.practice

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.salticusteam.vifi.core.data.notifications.admin.FirebaseStatus
import com.salticusteam.vifi.core.data.repository.PracticeService
import com.salticusteam.vifi.core.data.repository.SubscriptionProofProvider
import com.salticusteam.vifi.core.model.PracticeBatch
import com.salticusteam.vifi.core.model.PracticeError
import com.salticusteam.vifi.core.model.PracticeRequest
import com.salticusteam.vifi.core.util.LogCategory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Gets practice questions from the `nextPracticeQuestions` callable (europe-west1, injected by FirebaseModule), which
 * counts the free daily limit per account (iOS `FirebasePracticeService`). A Premium subscriber's Play subscription
 * is sent as proof with every call (`PracticeCalls.payload`); the server verifies it, and no proof or an invalid one
 * means the free rules, never an error. The App Check token is attached to the call automatically.
 *
 * Added in 4.2.0 — the live flavor's practice service.
 */
class FirebasePracticeService @Inject constructor(
    private val functions: FirebaseFunctions,
    private val proofProvider: SubscriptionProofProvider,
) : PracticeService {

    override suspend fun nextQuestions(request: PracticeRequest): PracticeBatch {
        val proof = proofProvider.currentProof()
        val data = try {
            functions.getHttpsCallable(PracticeCalls.NEXT_PRACTICE_QUESTIONS)
                .withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .call(PracticeCalls.payload(request, proof))
                .await()
                .getData()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw practiceError(e)
        }
        return PracticeParser.batch(data) ?: throw PracticeError.InvalidResponse
    }

    internal companion object {
        /** One question is a single read and a counter update; well under the SDK's 70 s default. */
        const val TIMEOUT_SECONDS = 30L

        /**
         * Maps a Functions or transport failure to a [PracticeError] (iOS `practiceError(from:)`), logging what it
         * cannot classify. Never carries the SDK's (English) message.
         */
        fun practiceError(error: Throwable): PracticeError {
            val mapped = when (error) {
                is PracticeError -> error
                is FirebaseFunctionsException ->
                    practiceError(FirebaseStatus.named(error.code.name))
                        // The SDK reports a request that never reached the server as INTERNAL around the IOException.
                        ?: (error.cause as? IOException)?.let { PracticeError.Offline }
                is IOException -> PracticeError.Offline
                else -> null
            }
            if (mapped != null) return mapped
            LogCategory.APP.log.e(error, "Practice questions failed")
            return PracticeError.Unknown
        }

        fun practiceError(status: FirebaseStatus): PracticeError? = when (status) {
            FirebaseStatus.PERMISSION_DENIED,
            FirebaseStatus.UNAUTHENTICATED -> PracticeError.NotSignedIn
            FirebaseStatus.UNAVAILABLE,
            FirebaseStatus.DEADLINE_EXCEEDED -> PracticeError.Offline
            FirebaseStatus.INVALID_ARGUMENT,
            FirebaseStatus.FAILED_PRECONDITION,
            FirebaseStatus.OTHER -> null
        }
    }
}
