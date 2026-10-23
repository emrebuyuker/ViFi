package com.salticusteam.vifi.core.model

// Added in 2.5 — moderation failures, with Turkish messages for the admin UI.
sealed class ModerationError(val userMessage: String) : Exception(userMessage) {

    /** The submission is no longer pending (already approved or rejected, perhaps by another admin). */
    data object NotPending : ModerationError("Bu gönderim artık beklemede değil; listeyi yenile.")

    /** The reward was already credited for this submission. */
    data object AlreadyCredited : ModerationError("Bu gönderim için ödül zaten verilmiş.")

    data object Offline : ModerationError("İnternet bağlantısı yok. Bağlantını kontrol edip tekrar dene.")

    data object PermissionDenied : ModerationError("Bu işlem için yetkin yok.")

    data class Unknown(override val message: String) : ModerationError(message)
}
