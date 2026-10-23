package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.AuthService
import com.salticusteam.vifi.core.model.AuthError
import com.salticusteam.vifi.core.model.AuthUser
import com.salticusteam.vifi.core.model.PhoneNumber
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory phone sign-in for the mock flavor, previews and tests (iOS MockAuthService). Accepts only
 * [VALID_CODE]; any well-formed Turkish mobile number receives a "code".
 */
class FakeAuthService(
    isSignedIn: Boolean = true,
    private val latency: Duration = 300.milliseconds,
    private val requiresRecentLoginForDeletion: Boolean = false,
    private val isAdmin: Boolean = false,
) : AuthService {

    private val user = MutableStateFlow(if (isSignedIn) DEFAULT_USER else null)

    /** Numbers that codes were sent to, by verification id. */
    private val pendingNumbers = mutableMapOf<String, String>()
    private var isRecentlyAuthenticated = false

    /** Thrown by [sendVerificationCode] instead of sending, to exercise the failure paths. */
    var sendFailure: AuthError? = null

    /** Thrown by [signIn] instead of checking the code, to exercise the failure paths. */
    var signInFailure: AuthError? = null

    /** When set, [sendVerificationCode] reports an on-device verification (no code to type), as Play services can. */
    var verifiesInstantly = false

    /** Every number a code was requested for, in order (for tests). */
    val sentCodeNumbers = mutableListOf<String>()

    override val currentUser: AuthUser? get() = user.value

    override fun userChanges(): Flow<AuthUser?> = user

    override suspend fun isCurrentUserAdmin(): Boolean = user.value != null && isAdmin

    override suspend fun sendVerificationCode(phoneNumber: String): String {
        delay(latency)
        sendFailure?.let { throw it }
        if (PhoneNumber.fromE164(phoneNumber) == null) throw AuthError.InvalidPhoneNumber
        sentCodeNumbers += phoneNumber
        if (verifiesInstantly) {
            isRecentlyAuthenticated = true
            return AuthService.INSTANT_VERIFICATION_ID
        }
        val verificationId = "mock-verification-${UUID.randomUUID()}"
        pendingNumbers[verificationId] = phoneNumber
        return verificationId
    }

    override suspend fun signIn(verificationId: String, code: String): AuthUser {
        val phoneNumber = verify(verificationId, code)
        val signedIn = AuthUser(id = DEFAULT_USER.id, phoneNumber = phoneNumber)
        isRecentlyAuthenticated = true
        user.value = signedIn
        return signedIn
    }

    override suspend fun reauthenticate(verificationId: String, code: String) {
        if (user.value == null) throw AuthError.NotSignedIn
        if (verificationId != AuthService.INSTANT_VERIFICATION_ID) verify(verificationId, code)
        isRecentlyAuthenticated = true
    }

    override fun signOut() {
        isRecentlyAuthenticated = false
        user.value = null
    }

    override suspend fun deleteAccount() {
        delay(latency)
        if (user.value == null) throw AuthError.NotSignedIn
        if (requiresRecentLoginForDeletion && !isRecentlyAuthenticated) throw AuthError.RequiresRecentLogin
        signOut()
    }

    private suspend fun verify(verificationId: String, code: String): String {
        delay(latency)
        signInFailure?.let { throw it }
        val phoneNumber = pendingNumbers[verificationId] ?: throw AuthError.CodeExpired
        if (code != VALID_CODE) throw AuthError.InvalidCode
        return phoneNumber
    }

    companion object {
        /** The only code accepted. */
        const val VALID_CODE = "111111"

        /** The user signed in at launch (a fictional number). */
        val DEFAULT_USER = AuthUser(id = "mock-user", phoneNumber = "+905321234567")
    }
}
