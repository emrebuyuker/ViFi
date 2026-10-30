package com.salticusteam.vifi.core.model

// Added in 4.1.0 — why an inbox source could not be read; kept apart from "nothing there" (iOS InboxError).
/** A failed listener never shows up as an empty inbox: the stream throws this and ends. */
sealed class InboxError(val userMessage: String) : Exception(userMessage) {

    data object Unavailable : InboxError("Bildirimler yüklenemedi. Bağlantını kontrol edip tekrar dene.")
}
