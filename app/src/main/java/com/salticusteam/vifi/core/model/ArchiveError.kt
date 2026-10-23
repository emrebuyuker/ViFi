package com.salticusteam.vifi.core.model

// Added in 2.5 — errors surfaced to the user by the archive data layer.
/** UI shows only [userMessage]; it is inline Turkish (the narrow exception to the strings.xml rule). */
sealed class ArchiveError(val userMessage: String) : Exception(userMessage) {

    /** The node does not exist or has no files. */
    data object NotFound : ArchiveError("İçerik bulunamadı.")

    /** Database rules rejected the read. */
    data object PermissionDenied : ArchiveError("Bu içeriğe şu anda erişilemiyor.")

    /** No connection or a timeout. */
    data object Offline : ArchiveError("İnternet bağlantısı yok. Bağlantını kontrol edip tekrar dene.")

    /** The file host refused the download (e.g. an HTTP 4xx/5xx status). */
    data class FileUnavailable(val statusCode: Int) :
        ArchiveError("Dosya şu anda sunucudan alınamıyor. Lütfen daha sonra tekrar dene.")

    data class Unknown(override val message: String) : ArchiveError(message)
}
