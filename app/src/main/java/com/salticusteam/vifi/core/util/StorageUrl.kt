package com.salticusteam.vifi.core.util

/**
 * Builds the canonical Firebase Storage download URL for an object at a `storagePath` (iOS `StorageURL`).
 *
 * The archive stores the stable path, not a token-bearing download URL (tokens get revoked); the result carries
 * no token — the file loader adds the user's ID token and App Check header.
 * Format: `https://firebasestorage.googleapis.com/v0/b/<bucket>/o/<encoded path>?alt=media`, the whole object
 * path being one percent-encoded segment ("/" → "%2F"), exactly as Firebase encodes object names.
 */
object StorageUrl {

    /** The canonical URL, or `null` when either input is empty. [storagePath] has no leading slash. */
    fun canonical(storagePath: String, bucket: String): String? {
        if (storagePath.isEmpty() || bucket.isEmpty()) return null
        return "https://${FirebaseConstants.STORAGE_HOST}/v0/b/$bucket/o/${encodeObjectPath(storagePath)}?alt=media"
    }

    /** Leaves only RFC 3986 unreserved characters (`A-Z a-z 0-9 - . _ ~`); everything else is UTF-8 %XX. */
    private fun encodeObjectPath(path: String): String = buildString {
        for (byte in path.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt().toChar()
            if (byte >= 0 && (c.isLetterOrDigit() && c.code < 128 || c in "-._~")) {
                append(c)
            } else {
                append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
    }
}
