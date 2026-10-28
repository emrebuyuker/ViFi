package com.salticusteam.vifi.core.model

// Added in 4.0.0 — Premium and paid-unlock purchase failures, with Turkish messages for the UI (iOS SubscriptionError).
sealed class SubscriptionError(val userMessage: String) : Exception(userMessage) {

    /** Google Play returned no product for the id (not yet active in Play Console, or a wrong id). */
    data object ProductUnavailable :
        SubscriptionError("Abonelik şu anda mağazadan alınamıyor. Lütfen daha sonra tekrar dene.")

    /** Google Play could not be reached (no connection, or the Play Store app is missing or out of date). */
    data object StoreUnavailable :
        SubscriptionError("Google Play'e ulaşılamıyor. Bağlantını kontrol edip tekrar dene.")

    /** The purchase could not be verified (the server rejected it); nothing was unlocked. */
    data object FailedVerification : SubscriptionError("Satın alma doğrulanamadı. Lütfen tekrar dene.")

    /** Any other failure; never carries Play's or the server's technical (English) message. */
    data object Unknown : SubscriptionError(GENERIC_ERROR_MESSAGE)

    /** A stable, low-cardinality reason for analytics (never the localized message). */
    val analyticsReason: String
        get() = when (this) {
            ProductUnavailable -> "product_unavailable"
            StoreUnavailable -> "store_unavailable"
            FailedVerification -> "failed_verification"
            Unknown -> "unknown"
        }
}
