package com.salticusteam.vifi.core.data.firebase

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.salticusteam.vifi.BuildConfig
import com.salticusteam.vifi.core.data.repository.AuthService
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.AuthError
import com.salticusteam.vifi.core.model.AuthUser
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import com.salticusteam.vifi.core.util.ForegroundActivityHolder
import com.salticusteam.vifi.core.util.LaunchArg
import com.salticusteam.vifi.core.util.LaunchArgs
import com.salticusteam.vifi.core.util.LogCategory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Phone number sign-in with Firebase Authentication (iOS FirebaseAuthService).
 *
 * Android differences: verification needs the host Activity for its reCAPTCHA fallback (taken weakly from
 * [ForegroundActivityHolder]); a resend reuses the `ForceResendingToken` of the previous code for that number;
 * and Play services may verify the number on its own (instant verification or SMS auto-retrieval), in which
 * case this signs in without the user typing the code and the session gate takes over.
 */
class FirebaseAuthService @Inject constructor(
    private val auth: FirebaseAuth,
    private val activityHolder: ForegroundActivityHolder,
    @ApplicationScope private val scope: CoroutineScope,
) : AuthService {

    /** The last resend token per E.164 number; without it "Kodu tekrar gönder" would not send a new SMS. */
    private val resendTokens = ConcurrentHashMap<String, PhoneAuthProvider.ForceResendingToken>()

    init {
        // SMS texts and the reCAPTCHA page in Turkish.
        auth.setLanguageCode("tr")
    }

    override val currentUser: AuthUser? get() = auth.currentUser?.toAuthUser()

    override fun userChanges(): Flow<AuthUser?> = callbackFlow {
        // Called right away with the restored user, then on every sign-in and sign-out.
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.conflate()

    override suspend fun isCurrentUserAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return try {
            user.getIdToken(false).await().claims["admin"] == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.APP.log.e(e, "Reading admin claim failed")
            false
        }
    }

    override suspend fun sendVerificationCode(phoneNumber: String): String {
        applyTestingSettings()
        val activity = activityHolder.activity ?: run {
            LogCategory.APP.log.e("Phone verification requested without a host Activity")
            throw AuthError.AppVerificationFailed
        }
        val result = CompletableDeferred<String>()
        // Collected in the app scope, not the caller's: an SMS auto-retrieved after the code was sent must still
        // sign the user in. The flow ends on verification, failure or the auto-retrieval timeout.
        scope.launch {
            try {
                collectVerification(activity, phoneNumber, result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result.completeExceptionally(authError(e, "Sending the verification code"))
            }
            // The flow ended without a code (should not happen); never leave the caller waiting.
            result.completeExceptionally(AuthError.Unknown(GENERIC_ERROR_MESSAGE))
        }
        return result.await()
    }

    override suspend fun signIn(verificationId: String, code: String): AuthUser {
        // Play services already signed the user in; nothing was there to type the code into.
        if (verificationId == INSTANT_VERIFICATION_ID) return currentUser ?: throw AuthError.NotSignedIn
        return signIn(PhoneAuthProvider.getCredential(verificationId, code))
    }

    override suspend fun reauthenticate(verificationId: String, code: String) {
        val user = auth.currentUser ?: throw AuthError.NotSignedIn
        // Play services verified the number and signed the same user in again moments ago: already recent.
        if (verificationId == INSTANT_VERIFICATION_ID) return
        try {
            user.reauthenticate(PhoneAuthProvider.getCredential(verificationId, code)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw authError(e, "Reauthenticating")
        }
    }

    override fun signOut() {
        auth.signOut()
    }

    override suspend fun deleteAccount() {
        val user = auth.currentUser ?: throw AuthError.NotSignedIn
        try {
            user.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw authError(e, "Deleting the account")
        }
    }

    // Helpers
    private suspend fun signIn(credential: PhoneAuthCredential): AuthUser =
        try {
            auth.signInWithCredential(credential).await().user?.toAuthUser() ?: throw AuthError.NotSignedIn
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw authError(e, "Signing in")
        }

    private suspend fun collectVerification(
        activity: Activity,
        phoneNumber: String,
        result: CompletableDeferred<String>,
    ) {
        verificationEvents(activity, phoneNumber, resendTokens[phoneNumber]).collect { event ->
            when (event) {
                is VerificationEvent.CodeSent -> {
                    resendTokens[phoneNumber] = event.resendToken
                    result.complete(event.verificationId)
                }
                is VerificationEvent.Verified -> try {
                    signIn(event.credential)
                    result.complete(INSTANT_VERIFICATION_ID)
                } catch (e: AuthError) {
                    if (!result.completeExceptionally(e)) LogCategory.APP.log.w("Automatic sign-in failed")
                }
                is VerificationEvent.Failed ->
                    result.completeExceptionally(authError(event.error, "Sending the verification code"))
            }
        }
    }

    /** `PhoneAuthProvider.verifyPhoneNumber` as a flow; callbacks arrive on the main thread. */
    private fun verificationEvents(
        activity: Activity,
        phoneNumber: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
    ): Flow<VerificationEvent> = callbackFlow {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                trySend(VerificationEvent.CodeSent(verificationId, token))
            }

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                trySend(VerificationEvent.Verified(credential))
                close()
            }

            override fun onVerificationFailed(error: FirebaseException) {
                trySend(VerificationEvent.Failed(error))
                close()
            }

            override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
                close()
            }
        }
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(AUTO_RETRIEVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .apply { resendToken?.let(::setForceResendingToken) }
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
        // Firebase offers no way to unregister the callbacks; closing the channel makes late ones no-ops.
        awaitClose()
    }

    /** AUTH-11: skips app verification for the console's fictional test numbers; debug builds only. */
    private fun applyTestingSettings() {
        if (BuildConfig.DEBUG && LaunchArgs.isSet(LaunchArg.PHONE_AUTH_TESTING)) {
            auth.firebaseAuthSettings.setAppVerificationDisabledForTesting(true)
            LogCategory.APP.log.i("Phone auth app verification is disabled for testing")
        }
    }

    private sealed interface VerificationEvent {
        data class CodeSent(
            val verificationId: String,
            val resendToken: PhoneAuthProvider.ForceResendingToken,
        ) : VerificationEvent

        data class Verified(val credential: PhoneAuthCredential) : VerificationEvent

        data class Failed(val error: FirebaseException) : VerificationEvent
    }

    internal companion object {
        /** Returned when the number was verified without a code; [signIn] then just reports the user. */
        const val INSTANT_VERIFICATION_ID = AuthService.INSTANT_VERIFICATION_ID

        /** How long Play services waits for the SMS to auto-retrieve (Firebase's maximum is 120). */
        private const val AUTO_RETRIEVAL_TIMEOUT_SECONDS = 60L

        /** Maps and logs a failure; the log carries the error type and code, never the phone number. */
        fun authError(error: Throwable, operation: String): AuthError {
            val mapped = FirebaseAuthErrors.authError(error)
            if (mapped !== error) LogCategory.APP.log.e(error, "%s failed (%s)", operation, mapped.analyticsReason)
            return mapped
        }

        private fun FirebaseUser.toAuthUser() = AuthUser(id = uid, phoneNumber = phoneNumber)
    }
}
