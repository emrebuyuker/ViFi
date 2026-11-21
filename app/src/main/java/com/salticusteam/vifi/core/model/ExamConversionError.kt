package com.salticusteam.vifi.core.model

// Added in 4.2.0 — conversion and subject-mapping failures, with Turkish messages for the admin UI (iOS `ExamConversionError`).
sealed class ExamConversionError(val userMessage: String) : Exception(userMessage) {

    data object Offline : ExamConversionError("İnternet bağlantısı yok. Bağlantını kontrol edip tekrar dene.")

    data object PermissionDenied : ExamConversionError("Bu işlem için yetkin yok.")

    data object NotFound : ExamConversionError("İş ya da soru bulunamadı; listeyi yenile.")

    /** The monthly AI budget is used up (`resource-exhausted`). */
    data object BudgetExceeded : ExamConversionError("Aylık yapay zekâ bütçesi doldu.")

    /** The job or question does not allow the action (`failed-precondition`); [serverMessage] is Turkish, shown as is. */
    data class NotAllowed(val serverMessage: String) : ExamConversionError(serverMessage)

    /** The server refused the input (`invalid-argument`); [serverMessage] is Turkish, shown as is. */
    data class InvalidInput(val serverMessage: String) : ExamConversionError(serverMessage)

    /** No conversion service is wired (a build without one). */
    data object ServiceUnavailable : ExamConversionError("Dönüştürme servisi kullanılamıyor.")

    /** No subject-mapping service is wired (a build without one). */
    data object SubjectMappingUnavailable : ExamConversionError("Ders eşleme servisi kullanılamıyor.")

    /** Any other failure; never carries the SDK's technical (English) message. */
    data object Unknown : ExamConversionError(GENERIC_ERROR_MESSAGE)
}
