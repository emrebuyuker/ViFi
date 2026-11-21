package com.salticusteam.vifi.core.model

// Added in 4.2.0 — practice-mode failures, with Turkish messages for the UI (iOS `PracticeError`).
sealed class PracticeError(val userMessage: String) : Exception(userMessage) {

    data object Offline : PracticeError("İnternet bağlantısı yok. Bağlantını kontrol edip tekrar dene.")

    data object NotSignedIn : PracticeError("Soruları görmek için giriş yapmış olman gerekiyor.")

    /** The server answered with something the app cannot read, or no practice service is available. */
    data object InvalidResponse : PracticeError("Sorular şu anda alınamadı. Lütfen daha sonra tekrar dene.")

    /** Any other failure; never carries the SDK's technical (English) message. */
    data object Unknown : PracticeError(GENERIC_ERROR_MESSAGE)
}
