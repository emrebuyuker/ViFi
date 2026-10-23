package com.salticusteam.vifi.core.model

// Added in 2.5 — profile read/write errors, with messages shown to the user.
sealed class ProfileError(val userMessage: String) : Exception(userMessage) {

    /** Nobody is signed in, or the uid does not match the signed-in user. */
    data object NotSignedIn : ProfileError("Oturumun sona erdi. Lütfen yeniden giriş yap.")

    /** Database rules rejected the read or write. */
    data object PermissionDenied :
        ProfileError("Profilin şu anda kaydedilemiyor. Lütfen daha sonra tekrar dene.")

    /** No connection or a timeout. */
    data object Offline : ProfileError("İnternet bağlantısı yok. Bağlantını kontrol edip tekrar dene.")

    data class Unknown(override val message: String) : ProfileError(message)
}
