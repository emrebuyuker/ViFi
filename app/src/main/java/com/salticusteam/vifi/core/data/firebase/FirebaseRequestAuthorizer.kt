package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.salticusteam.vifi.core.data.files.ExamFileUrls
import com.salticusteam.vifi.core.data.repository.RequestAuthorizer
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.util.LogCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Authorizes Firebase Storage downloads the way the Storage SDK does (iOS `FirebaseRequestAuthorizer`): the
 * user's ID token in `Authorization: Firebase <token>` and the App Check token in `X-Firebase-AppCheck`.
 *
 * Credentials only go to the app's own [bucket]: a URL naming any other bucket is refused rather than sent
 * the user's tokens.
 */
class FirebaseRequestAuthorizer(
    private val auth: FirebaseAuth,
    private val appCheck: FirebaseAppCheck?,
    private val bucket: String,
) : RequestAuthorizer {

    override suspend fun authorizationHeaders(url: String, forceRefresh: Boolean): Map<String, String> {
        if (!ExamFileUrls.isObject(url, bucket)) {
            LogCategory.FILES.log.e("Refusing to authorize a URL outside the app's Storage bucket")
            throw ArchiveError.PermissionDenied
        }
        val user = auth.currentUser ?: throw ArchiveError.PermissionDenied

        val idToken = try {
            user.getIdToken(forceRefresh).await().token
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.FILES.log.e(e, "Fetching the ID token failed")
            throw if (e is FirebaseNetworkException) ArchiveError.Offline else ArchiveError.PermissionDenied
        } ?: throw ArchiveError.PermissionDenied

        return buildMap {
            put(AUTHORIZATION_HEADER, "Firebase $idToken")
            appCheckToken()?.let { put(APP_CHECK_HEADER, it) }
        }
    }

    /**
     * The cached App Check token, refreshed by the SDK before it expires. Never force-refreshed: every forced
     * refresh is a Play Integrity round trip with a strict quota. A missing token is logged and the request is
     * sent without it; enforcement then rejects it like the SDK would.
     */
    private suspend fun appCheckToken(): String? {
        val appCheck = appCheck ?: return null
        return try {
            appCheck.getAppCheckToken(false).await().token
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.FILES.log.e(e, "Fetching the App Check token failed")
            null
        }
    }

    companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val APP_CHECK_HEADER = "X-Firebase-AppCheck"
    }
}
