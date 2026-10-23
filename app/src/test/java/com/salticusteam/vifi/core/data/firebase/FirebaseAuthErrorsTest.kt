package com.salticusteam.vifi.core.data.firebase

import com.salticusteam.vifi.core.model.AuthError
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Firebase exception types need Android; they are covered by the instrumented FirebaseAuthErrorMappingTest. */
class FirebaseAuthErrorsTest {

    @Test
    fun `authError maps Android error codes to domain errors`() {
        val expected = mapOf(
            "ERROR_INVALID_PHONE_NUMBER" to AuthError.InvalidPhoneNumber,
            "ERROR_INVALID_VERIFICATION_CODE" to AuthError.InvalidCode,
            "ERROR_MISSING_VERIFICATION_CODE" to AuthError.InvalidCode,
            "ERROR_SESSION_EXPIRED" to AuthError.CodeExpired,
            "ERROR_INVALID_VERIFICATION_ID" to AuthError.CodeExpired,
            "ERROR_TOO_MANY_REQUESTS" to AuthError.TooManyRequests,
            "ERROR_QUOTA_EXCEEDED" to AuthError.TooManyRequests,
            "ERROR_NETWORK_REQUEST_FAILED" to AuthError.Offline,
            "ERROR_WEB_CONTEXT_CANCELED" to AuthError.VerificationCancelled,
            "ERROR_APP_NOT_AUTHORIZED" to AuthError.AppVerificationFailed,
            "ERROR_INVALID_PLAY_INTEGRITY_TOKEN" to AuthError.AppVerificationFailed,
            "ERROR_REQUIRES_RECENT_LOGIN" to AuthError.RequiresRecentLogin,
            "ERROR_USER_DISABLED" to AuthError.AccountDisabled,
            "ERROR_USER_TOKEN_EXPIRED" to AuthError.NotSignedIn,
            "ERROR_USER_NOT_FOUND" to AuthError.NotSignedIn,
        )
        expected.forEach { (code, error) -> assertEquals(code, error, FirebaseAuthErrors.authError(code)) }
    }

    @Test
    fun `authError when code unknown falls back to the generic message`() {
        assertEquals(AuthError.Unknown(GENERIC_ERROR_MESSAGE), FirebaseAuthErrors.authError("ERROR_SOMETHING_NEW"))
    }

    @Test
    fun `authError when message carries error code 39 is sms temporarily unavailable`() {
        val error = RuntimeException("An internal error has occurred. [ Error code: 39 ]")

        assertEquals(AuthError.SmsTemporarilyUnavailable, FirebaseAuthErrors.authError(error))
    }

    @Test
    fun `isSmsTemporarilyUnavailable looks through the cause chain`() {
        val wrapped = IllegalStateException("wrapper", RuntimeException("503: ERROR CODE:39"))

        assertTrue(FirebaseAuthErrors.isSmsTemporarilyUnavailable(wrapped))
        assertFalse(FirebaseAuthErrors.isSmsTemporarilyUnavailable(RuntimeException("Error code: 17")))
    }

    @Test
    fun `authError when transport fails is offline and domain errors pass through`() {
        assertEquals(AuthError.Offline, FirebaseAuthErrors.authError(IOException("reset")))
        assertEquals(AuthError.InvalidCode, FirebaseAuthErrors.authError(AuthError.InvalidCode))
        assertEquals(AuthError.Unknown(GENERIC_ERROR_MESSAGE), FirebaseAuthErrors.authError(IllegalStateException()))
    }

    @Test
    fun `backendCode parses the number after error code`() {
        assertEquals(39, FirebaseAuthErrors.backendCode("We have blocked... Error code: 39"))
        assertNull(FirebaseAuthErrors.backendCode("no code here"))
        assertNull(FirebaseAuthErrors.backendCode(null))
    }
}
