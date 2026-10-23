package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException
import com.salticusteam.vifi.core.model.AuthError
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import java.io.IOException

/**
 * Maps Firebase Auth failures to [AuthError] (iOS `FirebaseAuthService.authError(from:)`). Android reports
 * string error codes (`ERROR_INVALID_VERIFICATION_CODE`), not iOS's numeric ones, so they are matched by name.
 */
internal object FirebaseAuthErrors {

    /** Google's SMS anti-abuse rejection, reported as "Error code: 39". */
    const val SMS_ANTI_ABUSE_BACKEND_CODE = 39

    /** How many causes are followed, so a cyclic chain can never loop forever. */
    private const val MAX_CHAIN_LENGTH = 8

    private val backendCodePattern = Regex("error code:\\s*(\\d+)", RegexOption.IGNORE_CASE)

    fun authError(error: Throwable): AuthError = when {
        error is AuthError -> error
        // Checked first: the rejection arrives as a vague internal or too-many-requests error.
        isSmsTemporarilyUnavailable(error) -> AuthError.SmsTemporarilyUnavailable
        error is FirebaseAuthMissingActivityForRecaptchaException -> AuthError.AppVerificationFailed
        error is FirebaseAuthException -> authError(error.errorCode)
        error is FirebaseTooManyRequestsException -> AuthError.TooManyRequests
        error is FirebaseNetworkException || error is IOException -> AuthError.Offline
        else -> AuthError.Unknown(GENERIC_ERROR_MESSAGE)
    }

    fun authError(errorCode: String): AuthError = when (errorCode) {
        "ERROR_INVALID_PHONE_NUMBER", "ERROR_MISSING_PHONE_NUMBER" -> AuthError.InvalidPhoneNumber
        "ERROR_INVALID_VERIFICATION_CODE", "ERROR_MISSING_VERIFICATION_CODE" -> AuthError.InvalidCode
        "ERROR_SESSION_EXPIRED", "ERROR_INVALID_VERIFICATION_ID", "ERROR_MISSING_VERIFICATION_ID" ->
            AuthError.CodeExpired
        "ERROR_TOO_MANY_REQUESTS", "ERROR_QUOTA_EXCEEDED" -> AuthError.TooManyRequests
        "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.Offline
        "ERROR_WEB_CONTEXT_CANCELED" -> AuthError.VerificationCancelled
        "ERROR_APP_NOT_AUTHORIZED", "ERROR_MISSING_CLIENT_IDENTIFIER", "ERROR_INVALID_APP_CREDENTIAL",
        "ERROR_MISSING_APP_CREDENTIAL", "ERROR_CAPTCHA_CHECK_FAILED", "ERROR_WEB_CONTEXT_ALREADY_PRESENTED",
        "ERROR_WEB_INTERNAL_ERROR", "ERROR_INVALID_CERT_HASH", "ERROR_MISSING_RECAPTCHA_TOKEN",
        "ERROR_INVALID_RECAPTCHA_TOKEN", "ERROR_RECAPTCHA_NOT_ENABLED", "ERROR_MISSING_RECAPTCHA_VERSION",
        "ERROR_INVALID_RECAPTCHA_VERSION", "ERROR_INVALID_RECAPTCHA_ACTION", "ERROR_RECAPTCHA_SDK_NOT_LINKED",
        "ERROR_INVALID_PLAY_INTEGRITY_TOKEN", "ERROR_MISSING_PLAY_INTEGRITY_TOKEN" -> AuthError.AppVerificationFailed
        "ERROR_REQUIRES_RECENT_LOGIN" -> AuthError.RequiresRecentLogin
        "ERROR_USER_DISABLED" -> AuthError.AccountDisabled
        "ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN", "ERROR_USER_NOT_FOUND" -> AuthError.NotSignedIn
        else -> AuthError.Unknown(GENERIC_ERROR_MESSAGE)
    }

    /**
     * Whether [error] is the SMS anti-abuse rejection. Android carries no structured backend body (iOS digs it out
     * of the user info), so the "Error code: 39" text is looked for in the messages of the cause chain.
     */
    fun isSmsTemporarilyUnavailable(error: Throwable): Boolean =
        generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }
            .take(MAX_CHAIN_LENGTH)
            .any { backendCode(it.message) == SMS_ANTI_ABUSE_BACKEND_CODE }

    /** The number after "Error code:" in [message], e.g. 39; `null` when there is none. */
    fun backendCode(message: String?): Int? =
        message?.let { backendCodePattern.find(it)?.groupValues?.get(1)?.toIntOrNull() }
}
