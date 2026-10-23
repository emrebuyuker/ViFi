package com.salticusteam.vifi.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.local.LocalDataCleaner
import com.salticusteam.vifi.core.data.profile.ProfileState
import com.salticusteam.vifi.core.data.profile.ProfileStore
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.AuthService
import com.salticusteam.vifi.core.data.repository.ProfileService
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.AuthError
import com.salticusteam.vifi.core.model.VerificationCode
import com.salticusteam.vifi.core.model.userMessage
import com.salticusteam.vifi.core.util.trackScreen
import com.salticusteam.vifi.feature.login.LoginViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The signed-in account (iOS AccountViewModel): the profile name and verified number, both followed live,
 * signing out, and deleting the account.
 *
 * Deleting removes the profile first, while the user is still signed in (the owner-only rules allow it), then
 * the account. When Firebase asks for a recent sign-in, a code is sent to the same number; once it is confirmed
 * the user is reauthenticated and the account deleted. Neither path navigates: the session gate replaces the
 * whole archive, this screen included, with the login screen, and the app-wide teardown resets the rest.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    profileStore: ProfileStore,
    private val authService: AuthService,
    private val profileService: ProfileService,
    private val localDataCleaner: LocalDataCleaner,
    private val analytics: AnalyticsTracker,
    /** The clock the resend cooldown is measured with (a fixed or stepping clock in tests). */
    private val clock: Clock,
    /** Outlives this screen: the purge after a deletion must finish after the session gate removed it. */
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    // State
    /** The deletion half of the state; the profile and phone fields are filled in from the stores. */
    private val _deletion = MutableStateFlow(AccountUiState())

    val uiState: StateFlow<AccountUiState> =
        combine(sessionStore.state, profileStore.state, _deletion, ::accountState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = accountState(sessionStore.state.value, profileStore.state.value, _deletion.value),
            )

    // Internal state
    /** The id the re-verification code must be combined with; set while [AccountUiState.isReverifying]. */
    private var verificationId: String? = null

    // Intents
    fun onScreenShown() {
        analytics.trackScreen(AnalyticsScreen.ACCOUNT)
    }

    /** Signs out after the user confirmed it. */
    fun signOut() {
        if (_deletion.value.isWorking) return
        authService.signOut()
        analytics.track(AnalyticsEvent.SignedOut)
    }

    /** Deletes the account after the user confirmed it, verifying the number again first when Firebase asks. */
    fun deleteAccount() {
        if (_deletion.value.isWorking) return
        val user = sessionStore.user ?: return fail(AuthError.NotSignedIn)
        _deletion.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                // Removed while still signed in, so a deleted account leaves no profile behind.
                profileService.deleteProfile(user.id)
                authService.deleteAccount()
                didDeleteAccount()
            } catch (e: AuthError.RequiresRecentLogin) {
                // The profile is already gone; only the account waits for the fresh sign-in.
                sendReverificationCode(user.phoneNumber)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Takes an edit of the code field: digits only, at most six, confirmed as soon as all six are there. */
    fun onCodeChange(input: String) {
        if (_deletion.value.isWorking) return
        val digits = VerificationCode.digits(input)
        _deletion.update { it.copy(codeText = digits, error = if (digits.isEmpty()) it.error else null) }
        if (digits.length == VerificationCode.LENGTH) confirmReverification()
    }

    /** Reauthenticates with the entered code, then deletes the account. */
    fun confirmReverification() {
        val id = verificationId
        val state = _deletion.value
        if (!state.canConfirmReverification || id == null) return
        reauthenticateAndDelete(id, state.codeText)
    }

    /** Sends a new re-verification code once the cooldown is over. */
    fun resendCode() {
        if (!canResendCode()) return
        _deletion.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch { sendReverificationCode(sessionStore.user?.phoneNumber) }
    }

    /** Leaves the code entry without deleting anything more (the profile is already gone, as on iOS). */
    fun cancelReverification() {
        if (_deletion.value.isWorking) return
        verificationId = null
        _deletion.value = AccountUiState()
    }

    /** Whole seconds left until another code can be requested, `0` once it can. */
    fun resendCooldownRemaining(at: Instant = clock.instant()): Int {
        val availableAt = _deletion.value.resendAvailableAt ?: return 0
        val millis = Duration.between(at, availableAt).toMillis()
        return if (millis <= 0) 0 else ((millis + 999) / 1000).toInt()
    }

    fun canResendCode(at: Instant = clock.instant()): Boolean {
        val state = _deletion.value
        return state.isReverifying && !state.isWorking && resendCooldownRemaining(at) == 0
    }

    // Helpers
    /** Sends a code to the account's own number (E.164) and shows its entry; failures land in the state. */
    private suspend fun sendReverificationCode(phoneNumber: String?) {
        try {
            if (phoneNumber == null) throw AuthError.RequiresRecentLogin
            val id = authService.sendVerificationCode(phoneNumber)
            if (id == AuthService.INSTANT_VERIFICATION_ID) {
                // Verified on the device: nothing to type, delete right away.
                reauthenticateAndDelete(id, code = "")
                return
            }
            verificationId = id
            _deletion.update {
                it.copy(
                    isWorking = false,
                    isReverifying = true,
                    codeText = "",
                    resendAvailableAt = clock.instant().plus(LoginViewModel.RESEND_COOLDOWN),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun reauthenticateAndDelete(id: String, code: String) {
        _deletion.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                authService.reauthenticate(id, code)
                // The profile was already removed by deleteAccount(), before the recent-login prompt.
                authService.deleteAccount()
                didDeleteAccount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /**
     * Reports the deletion and wipes the device's copy of the user's exam data. Runs in the app scope: the
     * session gate tears this screen down as soon as the account is gone.
     */
    private fun didDeleteAccount() {
        analytics.track(AnalyticsEvent.AccountDeleted)
        appScope.launch { localDataCleaner.purgeAccountData() }
    }

    private fun fail(error: Throwable) {
        val authError = error as? AuthError
        _deletion.update { state ->
            val failed = state.copy(isWorking = false, error = error.userMessage)
            when (authError) {
                // Ready for the next attempt; the message stays until the user types again.
                AuthError.InvalidCode -> failed.copy(codeText = "")
                // The code is useless now; a new one may be requested right away.
                AuthError.CodeExpired -> failed.copy(codeText = "", resendAvailableAt = null)
                else -> failed
            }
        }
    }

    private fun accountState(session: SessionState, profile: ProfileState, deletion: AccountUiState) =
        deletion.copy(
            profileName = (profile as? ProfileState.Present)?.profile?.fullName,
            formattedPhoneNumber = (session as? SessionState.SignedIn)?.user?.formattedPhoneNumber,
        )
}
