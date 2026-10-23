package com.salticusteam.vifi.core.data.files

import com.salticusteam.vifi.core.data.repository.RequestAuthorizer
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.util.LogCategory
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Sends Firebase Storage downloads by their canonical URL with the user's credentials (iOS
 * `RemoteFileLoader.download`), shared by the file loader and the image loader.
 *
 * A 401/403 answer (expired or revoked ID token) is retried once with a freshly minted ID token; a second
 * rejection fails with [ArchiveError.PermissionDenied]. Any other URL passes through untouched.
 */
class StorageAuthInterceptor(private val authorizer: RequestAuthorizer) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        if (!ExamFileUrls.isFirebaseStorageUrl(url)) return chain.proceed(request)

        val canonical = ExamFileUrls.canonical(url)
        val first = chain.proceed(authorized(request, canonical, forceRefresh = false))
        if (!first.isAuthorizationFailure) return first
        first.close()
        LogCategory.FILES.log.i("A download was rejected; retrying with a fresh token")

        val second = chain.proceed(authorized(request, canonical, forceRefresh = true))
        if (!second.isAuthorizationFailure) return second
        second.close()
        throw ArchiveIOException(ArchiveError.PermissionDenied)
    }

    private fun authorized(request: Request, canonicalUrl: String, forceRefresh: Boolean): Request {
        // OkHttp calls interceptors on its own worker threads, so blocking for the (usually cached) token is fine.
        val headers = try {
            runBlocking { authorizer.authorizationHeaders(canonicalUrl, forceRefresh) }
        } catch (e: ArchiveError) {
            throw ArchiveIOException(e)
        }
        return request.newBuilder()
            .url(canonicalUrl)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
    }

    /** Firebase Storage answers 401 for a missing or invalid token and 403 when rules deny the read. */
    private val Response.isAuthorizationFailure: Boolean get() = code == 401 || code == 403
}
