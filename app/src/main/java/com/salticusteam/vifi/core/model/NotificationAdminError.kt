package com.salticusteam.vifi.core.model

// Added in 4.1.0 — admin notification-sending failures, with Turkish messages for the composer (iOS NotificationAdminError).
sealed class NotificationAdminError(val userMessage: String) : Exception(userMessage) {

    data object PermissionDenied : NotificationAdminError("Bu işlem için yetkin yok.")

    /** The server refused the draft; [serverMessage] is its Turkish explanation, shown as is. */
    data class InvalidDraft(val serverMessage: String) : NotificationAdminError(serverMessage)

    /** The notification has already been sent (or canceled) and can no longer be canceled. */
    data object NotCancelable : NotificationAdminError("Bu bildirim gönderildiği için artık iptal edilemez.")

    /** The campaign image could not be uploaded; nothing was sent. */
    data object ImageUploadFailed : NotificationAdminError("Görsel yüklenemedi; bildirim gönderilmedi. Tekrar dene.")

    data object Offline : NotificationAdminError("İnternet bağlantısı yok. Bağlantını kontrol edip tekrar dene.")

    /** The server answered with something the app cannot read. */
    data object UnexpectedResponse : NotificationAdminError("Sunucudan beklenmeyen bir yanıt alındı.")

    /** Any other failure; never carries the server's or the SDK's technical (English) message. */
    data object Unknown : NotificationAdminError(GENERIC_ERROR_MESSAGE)
}
