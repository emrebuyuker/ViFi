package com.salticusteam.vifi.core.model

// Added in 2.5 — exam-unlock failures, with Turkish messages for the UI.
sealed class ExamUnlockError(val userMessage: String) : Exception(userMessage) {

    /** The balance is below the unlock cost. */
    data object InsufficientCoins : ExamUnlockError("Bu sınavı açmak için yeterli coin yok.")

    data object NotFound : ExamUnlockError("Sınav bulunamadı.")

    data object NotSignedIn : ExamUnlockError("Oturumun sona erdi. Lütfen yeniden giriş yap.")

    data object Offline : ExamUnlockError("İnternet bağlantısı yok. Bağlantını kontrol edip tekrar dene.")

    data class Unknown(override val message: String) : ExamUnlockError(message)

    /** A stable, low-cardinality reason for analytics (never the localized message). */
    val analyticsReason: String
        get() = when (this) {
            InsufficientCoins -> "insufficient_coins"
            NotFound -> "not_found"
            NotSignedIn -> "not_signed_in"
            Offline -> "offline"
            is Unknown -> "unknown"
        }
}
