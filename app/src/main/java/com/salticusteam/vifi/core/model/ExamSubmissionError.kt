package com.salticusteam.vifi.core.model

// Added in 2.5 — exam-submission errors, with messages shown to the user.
sealed class ExamSubmissionError(val userMessage: String) : Exception(userMessage) {

    /** Nobody is signed in, or the owner does not match the signed-in user. */
    data object NotSignedIn : ExamSubmissionError("Oturumun sona erdi. Lütfen yeniden giriş yap.")

    /** The submission does not satisfy the required invariants (should be caught by the form first). */
    data object InvalidSubmission :
        ExamSubmissionError("Gönderimin eksik veya geçersiz. Lütfen bilgileri kontrol et.")

    /** Database or storage rules rejected the request. */
    data object PermissionDenied :
        ExamSubmissionError("Gönderimin şu anda kaydedilemiyor. Lütfen daha sonra tekrar dene.")

    /** No connection or a timeout. */
    data object Offline :
        ExamSubmissionError("İnternet bağlantısı yok. Bağlantını kontrol edip tekrar dene.")

    data class Unknown(override val message: String) : ExamSubmissionError(message)

    /** A stable, low-cardinality reason for analytics (never the localized message). */
    val analyticsReason: String
        get() = when (this) {
            NotSignedIn -> "not_signed_in"
            InvalidSubmission -> "invalid_submission"
            PermissionDenied -> "permission_denied"
            Offline -> "offline"
            is Unknown -> "unknown"
        }
}
