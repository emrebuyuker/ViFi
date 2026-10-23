package com.salticusteam.vifi.core.data.repository

/**
 * Adds the signed-in user's credentials to downloads of protected exam files.
 *
 * Platform adaptation: iOS returns a `URLRequest`; here the implementation returns the headers and the
 * HTTP layer (OkHttp / Coil) applies them to its own request type.
 */
interface RequestAuthorizer {
    /**
     * HTTP headers carrying the user's ID token and, when available, an App Check token, for a download of [url].
     *
     * @param forceRefresh Fetches a fresh ID token instead of the cached one, after the server rejected a request.
     * @throws com.salticusteam.vifi.core.model.ArchiveError.PermissionDenied when nobody is signed in, or when
     * [url] is not an object of the app's own Storage bucket (credentials never go anywhere else).
     * @throws com.salticusteam.vifi.core.model.ArchiveError.Offline when the token cannot be refreshed for
     * lack of a connection.
     */
    suspend fun authorizationHeaders(url: String, forceRefresh: Boolean): Map<String, String>
}
