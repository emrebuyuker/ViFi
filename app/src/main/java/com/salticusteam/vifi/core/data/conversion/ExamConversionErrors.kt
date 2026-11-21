package com.salticusteam.vifi.core.data.conversion

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.salticusteam.vifi.core.model.ExamConversionError
import com.salticusteam.vifi.core.util.LogCategory
import java.io.IOException

/**
 * Maps a Functions, Firestore or transport failure of the admin conversion / subject-mapping calls to an
 * [ExamConversionError] (iOS `FirebaseExamConversionService.conversionError(from:)`). The mapping works on the
 * canonical status names both SDKs share ([fromStatus]), so it is unit-tested without the SDK's own enums; the
 * server's Turkish message travels with `failed-precondition` and `invalid-argument`, an unreadable failure is logged
 * and shown as the generic message (never the SDK's English text).
 */
object ExamConversionErrors {

    fun map(error: Throwable): ExamConversionError = when (error) {
        is ExamConversionError -> error
        is FirebaseFunctionsException ->
            fromStatus(error.code.name, error.message)
                // The SDK reports a request that never reached the server as INTERNAL around the IOException.
                ?: (error.cause as? IOException)?.let { ExamConversionError.Offline }
                ?: unknown(error)
        is FirebaseFirestoreException -> fromFirestoreStatus(error.code.name) ?: unknown(error)
        is IOException -> ExamConversionError.Offline
        else -> unknown(error)
    }

    /** A callable's status; `null` for a status the admin cannot act on. */
    fun fromStatus(status: String, message: String?): ExamConversionError? = when (status) {
        "PERMISSION_DENIED", "UNAUTHENTICATED" -> ExamConversionError.PermissionDenied
        "NOT_FOUND" -> ExamConversionError.NotFound
        "RESOURCE_EXHAUSTED" -> ExamConversionError.BudgetExceeded
        "FAILED_PRECONDITION" -> readable(message)?.let { ExamConversionError.NotAllowed(it) }
        "INVALID_ARGUMENT" -> readable(message)?.let { ExamConversionError.InvalidInput(it) }
        "UNAVAILABLE", "DEADLINE_EXCEEDED" -> ExamConversionError.Offline
        else -> null
    }

    /** A Firestore read or draft edit: no admin claim, a missing draft, or no connection. */
    fun fromFirestoreStatus(status: String): ExamConversionError? = when (status) {
        "PERMISSION_DENIED", "UNAUTHENTICATED" -> ExamConversionError.PermissionDenied
        "NOT_FOUND" -> ExamConversionError.NotFound
        "UNAVAILABLE", "DEADLINE_EXCEEDED" -> ExamConversionError.Offline
        else -> null
    }

    /** A refusal without a readable message is not shown as an empty error. */
    private fun readable(message: String?): String? = message?.trim()?.takeIf { it.isNotEmpty() }

    private fun unknown(error: Throwable): ExamConversionError {
        LogCategory.APP.log.e(error, "Exam conversion operation failed")
        return ExamConversionError.Unknown
    }
}
