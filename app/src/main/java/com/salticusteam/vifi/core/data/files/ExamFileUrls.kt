package com.salticusteam.vifi.core.data.files

import com.salticusteam.vifi.core.util.FirebaseConstants
import java.net.URI

/**
 * Identity of exam file URLs (iOS `RemoteFileLoader.canonicalURL` / `FirebaseRequestAuthorizer.isObject`).
 *
 * A Firebase Storage download URL is identified without its `token=` query item: tokens get revoked, and a
 * URL with another token still names the same file. The canonical URL keys in-flight downloads, the caches
 * and the local file folders, and is the URL actually requested (with the user's credentials).
 */
object ExamFileUrls {

    fun isFirebaseStorageUrl(url: String): Boolean {
        val uri = parse(url) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(FirebaseConstants.STORAGE_HOST, ignoreCase = true)
    }

    /** [url] without its `token` query item when it is a Firebase Storage URL; any other URL unchanged. */
    fun canonical(url: String): String {
        if (!isFirebaseStorageUrl(url)) return url
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val fragmentStart = url.indexOf('#', queryStart).takeIf { it >= 0 } ?: url.length
        val items = url.substring(queryStart + 1, fragmentStart).split('&')
        val kept = items.filterNot { it == "token" || it.startsWith("token=") }
        if (kept.size == items.size) return url
        val query = if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
        return url.substring(0, queryStart) + query + url.substring(fragmentStart)
    }

    /**
     * Whether [url] is a download URL of an object in [bucket] (`/v0/b/<bucket>/o/…`). Credentials only ever
     * go to the app's own bucket, like the Storage SDK.
     */
    fun isObject(url: String, bucket: String): Boolean {
        if (bucket.isEmpty() || !isFirebaseStorageUrl(url)) return false
        val path = parse(url)?.rawPath ?: return false
        return path.startsWith("/v0/b/$bucket/o/")
    }

    private fun parse(url: String): URI? = runCatching { URI(url) }.getOrNull()
}
