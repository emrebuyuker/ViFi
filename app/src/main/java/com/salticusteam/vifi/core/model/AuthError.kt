package com.salticusteam.vifi.core.model

// Added in 2.5 — sign-in and account errors, with messages shown to the user.
sealed class AuthError(val userMessage: String) : Exception(userMessage) {

    data object InvalidPhoneNumber :
        AuthError("Telefon numarası geçersiz. 5 ile başlayan 10 haneli cep telefonu numaranı gir.")

    data object InvalidCode :
        AuthError("Kod hatalı. SMS ile gelen 6 haneli kodu kontrol edip tekrar dene.")

    /** The code or the verification session expired; a new code is needed. */
    data object CodeExpired : AuthError("Kodun süresi doldu. Yeni bir kod iste.")

    /** Too many attempts from this device or number, or the SMS quota is exhausted. */
    data object TooManyRequests : AuthError("Çok fazla deneme yapıldı. Lütfen bir süre sonra tekrar dene.")

    /**
     * The SMS provider refused to send a code right now (Firebase's anti-abuse "Error code: 39"): a
     * temporary, server-side condition tied to the number, not a mistake the user can fix by retrying faster.
     */
    data object SmsTemporarilyUnavailable : AuthError(
        "SMS servisinde geçici bir sorun nedeniyle kod gönderilemedi. " +
            "Lütfen bir süre sonra tekrar dene; sorun sürerse bizimle iletişime geç.",
    )

    data object Offline : AuthError("İnternet bağlantısı yok. Bağlantını kontrol edip tekrar dene.")

    /** The user closed the verification (reCAPTCHA) page. */
    data object VerificationCancelled : AuthError("Doğrulama iptal edildi. Kod almak için tekrar dene.")

    /** The app could not prove it is genuine (Play Integrity, reCAPTCHA or App Check). */
    data object AppVerificationFailed : AuthError("Cihaz doğrulanamadı. Lütfen daha sonra tekrar dene.")

    /** The operation needs a recent sign-in; verify the number again. */
    data object RequiresRecentLogin : AuthError("Güvenliğin için numaranı yeniden doğrulaman gerekiyor.")

    /** The user is no longer signed in (e.g. the account was deleted elsewhere). */
    data object NotSignedIn : AuthError("Oturumun sona erdi. Lütfen yeniden giriş yap.")

    /** An admin disabled the account; signing in again will not help. */
    data object AccountDisabled : AuthError("Bu hesap devre dışı bırakıldı.")

    data class Unknown(override val message: String) : AuthError(message)

    /** A stable, low-cardinality reason for analytics (never the localized message). */
    val analyticsReason: String
        get() = when (this) {
            InvalidPhoneNumber -> "invalid_phone_number"
            InvalidCode -> "invalid_code"
            CodeExpired -> "code_expired"
            TooManyRequests -> "too_many_requests"
            SmsTemporarilyUnavailable -> "sms_temporarily_unavailable"
            Offline -> "offline"
            VerificationCancelled -> "verification_cancelled"
            AppVerificationFailed -> "app_verification_failed"
            RequiresRecentLogin -> "requires_recent_login"
            NotSignedIn -> "not_signed_in"
            AccountDisabled -> "account_disabled"
            is Unknown -> "unknown"
        }
}
