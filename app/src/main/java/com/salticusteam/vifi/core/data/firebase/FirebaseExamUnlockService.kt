package com.salticusteam.vifi.core.data.firebase

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.salticusteam.vifi.core.data.repository.ExamUnlockService
import com.salticusteam.vifi.core.model.ExamUnlockError
import com.salticusteam.vifi.core.model.ExamUnlockResult
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Unlocks an exam through the shared `unlockExam` callable (europe-west1, injected by FirebaseModule), which
 * debits the wallet and records the unlock in one server-side transaction (iOS FirebaseExamUnlockService).
 * The App Check token is attached to the call automatically.
 */
class FirebaseExamUnlockService @Inject constructor(
    private val functions: FirebaseFunctions,
) : ExamUnlockService {

    override suspend fun unlock(examId: String): ExamUnlockResult =
        try {
            val response = functions.getHttpsCallable(FUNCTION_NAME).call(mapOf("examId" to examId)).await()
            parseResult(response.getData())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw unlockError(e)
        }

    internal companion object {
        private const val FUNCTION_NAME = "unlockExam"
        private const val TAG = "ExamUnlock"

        /** `{ balance: number, alreadyUnlocked: boolean }`; missing fields fall back like iOS. */
        fun parseResult(data: Any?): ExamUnlockResult {
            val map = data as? Map<*, *> ?: emptyMap<Any, Any>()
            return ExamUnlockResult(
                balance = (map["balance"] as? Number)?.toInt() ?: 0,
                alreadyUnlocked = map["alreadyUnlocked"] as? Boolean ?: false,
            )
        }

        /** Maps a callable failure to a domain error (iOS `unlockError(from:)`). */
        fun unlockError(error: Throwable): ExamUnlockError {
            val mapped = when (error) {
                is FirebaseFunctionsException -> unlockError(error.code)
                is IOException -> ExamUnlockError.Offline
                else -> null
            }
            if (mapped != null) return mapped
            Log.e(TAG, "Exam unlock failed", error)
            // iOS shows localizedDescription here; Android never surfaces a raw exception message.
            return ExamUnlockError.Unknown(GENERIC_ERROR_MESSAGE)
        }

        fun unlockError(code: FirebaseFunctionsException.Code): ExamUnlockError? = when (code) {
            FirebaseFunctionsException.Code.FAILED_PRECONDITION -> ExamUnlockError.InsufficientCoins
            FirebaseFunctionsException.Code.NOT_FOUND -> ExamUnlockError.NotFound
            FirebaseFunctionsException.Code.PERMISSION_DENIED,
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> ExamUnlockError.NotSignedIn
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> ExamUnlockError.Offline
            else -> null
        }
    }
}
