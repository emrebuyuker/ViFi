package com.salticusteam.vifi.core.data.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.salticusteam.vifi.core.model.AuthError
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** On device because Firebase exception constructors check their arguments with Android's TextUtils. */
@RunWith(AndroidJUnit4::class)
class FirebaseAuthErrorMappingTest {

    @Test
    fun authError_mapsFirebaseExceptionsLikeIos() {
        assertEquals(
            AuthError.InvalidCode,
            FirebaseAuthErrors.authError(
                FirebaseAuthInvalidCredentialsException("ERROR_INVALID_VERIFICATION_CODE", "The code is invalid."),
            ),
        )
        assertEquals(
            AuthError.RequiresRecentLogin,
            FirebaseAuthErrors.authError(FirebaseAuthException("ERROR_REQUIRES_RECENT_LOGIN", "Log in again.")),
        )
        assertEquals(AuthError.TooManyRequests, FirebaseAuthErrors.authError(FirebaseTooManyRequestsException("Blocked.")))
        assertEquals(AuthError.Offline, FirebaseAuthErrors.authError(FirebaseNetworkException("Timeout.")))
    }

    @Test
    fun authError_whenTooManyRequestsCarriesErrorCode39_isSmsTemporarilyUnavailable() {
        val error = FirebaseTooManyRequestsException("We have blocked all requests. [ Error code: 39 ]")

        assertEquals(AuthError.SmsTemporarilyUnavailable, FirebaseAuthErrors.authError(error))
    }
}
