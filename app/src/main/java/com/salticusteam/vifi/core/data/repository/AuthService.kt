package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.AuthUser
import kotlinx.coroutines.flow.Flow

/**
 * Signs the user in with their phone number (SMS one-time code) and manages the account.
 *
 * The whole app requires a signed-in user: database and file rules reject anonymous requests. Every throwing
 * operation throws `AuthError` (or `CancellationException`).
 *
 * Platform adaptations: iOS `canHandle(_ url:)` (reCAPTCHA redirect handling) is dropped, since Android phone
 * verification needs no URL redirect. `sendVerificationCode` has no Activity parameter yet; how the live
 * implementation supplies `PhoneAuthOptions.setActivity` without retaining an Activity is decided in BE-04.
 */
interface AuthService {
    /**
     * The signed-in user, if any. May be `null` briefly at launch until the stored session is restored;
     * [userChanges] reports the authoritative state.
     */
    val currentUser: AuthUser?

    /** Emits the current user right away and again on every sign-in and sign-out. */
    fun userChanges(): Flow<AuthUser?>

    /**
     * Whether the signed-in user carries the `admin: true` custom claim (set out-of-band by the project
     * owner). Reads the ID token; returns `false` when signed out or on any error. Gates the moderation tools.
     */
    suspend fun isCurrentUserAdmin(): Boolean

    /**
     * Sends a verification code by SMS to [phoneNumber] (E.164) and returns the verification id that the code
     * must be combined with.
     */
    suspend fun sendVerificationCode(phoneNumber: String): String

    /** Signs in with a code sent by [sendVerificationCode]. */
    suspend fun signIn(verificationId: String, code: String): AuthUser

    /** Confirms the signed-in user's identity again with a fresh code, before a sensitive operation. */
    suspend fun reauthenticate(verificationId: String, code: String)

    /** Signs the user out. */
    fun signOut()

    /**
     * Permanently deletes the signed-in user's account.
     *
     * @throws com.salticusteam.vifi.core.model.AuthError.RequiresRecentLogin when the last sign-in is too
     * old; reauthenticate and try again.
     */
    suspend fun deleteAccount()
}
