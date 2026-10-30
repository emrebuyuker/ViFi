package com.salticusteam.vifi.core.model

// Added in 4.1.0 — why the campaign consent record could not be read (iOS NotificationSettingsError).
/** Kept apart from "never answered", so a failed listener never reads as a missing consent. */
sealed class NotificationSettingsError(val userMessage: String) : Exception(userMessage) {

    data object Unavailable :
        NotificationSettingsError("Bildirim tercihin şu an okunamıyor. Bağlantını kontrol edip tekrar dene.")
}
