package com.salticusteam.vifi.feature.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.billing.SubscriptionStore
import com.salticusteam.vifi.core.data.coins.CoinWalletStore
import com.salticusteam.vifi.core.data.local.LocalDataCleaner
import com.salticusteam.vifi.core.data.profile.ProfileState
import com.salticusteam.vifi.core.data.profile.ProfileStore
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.AuthService
import com.salticusteam.vifi.core.data.repository.DeviceRegistration
import com.salticusteam.vifi.core.data.repository.ProfileService
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.AuthError
import com.salticusteam.vifi.core.model.CoinWallet
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
 * The signed-in account (iOS AccountViewModel): the profile name, Premium state, coin balance and verified number,
 * all followed live, signing out, and deleting the account.
 *
 * Signing out and deleting both first remove this install's push registration, while the user can still write (iOS
 * `beforeSignOut`, at most 3 s, never fails). Deleting then removes the profile, still signed in (the owner-only rules
 * allow it), then the account. When Firebase asks for a recent sign-in, a code is sent to the same number; once it is
 * confirmed the user is reauthenticated and the account deleted. Neither path navigates: the session gate replaces the
 * whole archive, this screen included, with the login screen, and the app-wide teardown resets the rest.
 *
 * The re-verification (code entry, typed code, verification id, resend cooldown) lives in the [SavedStateHandle],
 * so a process death while the SMS is on its way brings the user back to the same code entry.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    profileStore: ProfileStore,
    coinWalletStore: CoinWalletStore,
    subscriptionStore: SubscriptionStore,
    private val authService: AuthService,
    private val profileService: ProfileService,
    /** This install's push registration, removed before signing out or deleting. */
    private val deviceRegistration: DeviceRegistration,
    private val localDataCleaner: LocalDataCleaner,
    private val analytics: AnalyticsTracker,
    /** The clock the resend cooldown is measured with (a fixed or stepping clock in tests). */
    private val clock: Clock,
    /**
     * Outlives this screen: a confirmed sign-out must finish even if the screen closes during its cleanup, and the
     * purge after a deletion must finish after the session gate removed it.
     */
    @ApplicationScope private val appScope: CoroutineScope,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // State
    /** The deletion half of the state; the profile and phone fields are filled in from the stores. */
    private val _deletion = MutableStateFlow(restoredDeletion())

    /** Whether the user is an admin; resolved from the ID token's claim when the screen opens (iOS `.task`). */
    private val _isAdmin = MutableStateFlow(false)

    val uiState: StateFlow<AccountUiState> =
        combine(sessionStore.state, profileStore.state, coinWalletStore.wallet, _deletion, _isAdmin, ::accountState)
            // Premium follows the Google account, not the signed-in user (4.0.0 DEC-03).
            .combine(subscriptionStore.isSubscribed) { state, isSubscribed -> state.copy(isSubscribed = isSubscribed) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = accountState(
                    sessionStore.state.value,
                    profileStore.state.value,
                    coinWalletStore.wallet.value,
                    _deletion.value,
                    _isAdmin.value,
                ).copy(isSubscribed = subscriptionStore.isSubscribed.value),
            )

    // Internal state
    /**
     * The id the re-verification code must be combined with; set while [AccountUiState.isReverifying]. Kept in
     * the SavedStateHandle, so it survives a process death.
     */
    private var verificationId: String?
        get() = savedStateHandle[KEY_VERIFICATION_ID]
        set(value) {
            savedStateHandle[KEY_VERIFICATION_ID] = value
        }

    // Intents
    init {
        viewModelScope.launch { _deletion.collect(::saveDeletion) }
    }

    fun onScreenShown() {
        analytics.trackScreen(AnalyticsScreen.ACCOUNT)
        // UI gating only: the rules' isAdmin() is what actually authorizes moderation writes.
        viewModelScope.launch { _isAdmin.value = authService.isCurrentUserAdmin() }
    }

    /**
     * Signs out after the user confirmed it (iOS `signOut() async`): waits for this install's push registration to be
     * removed while the user can still write, then ends the session. Ignored while a deletion or another sign-out runs.
     */
    fun signOut() {
        val state = _deletion.value
        if (state.isWorking || state.isSigningOut) return
        val uid = sessionStore.user?.id
        _deletion.update { it.copy(isSigningOut = true) }
        appScope.launch {
            try {
                uid?.let { deviceRegistration.unregister(it) }
                authService.signOut()
                analytics.track(AnalyticsEvent.SignedOut)
            } finally {
                _deletion.update { it.copy(isSigningOut = false) }
            }
        }
    }

    /** Deletes the account after the user confirmed it, verifying the number again first when Firebase asks. */
    fun deleteAccount() {
        val state = _deletion.value
        if (state.isWorking || state.isSigningOut) return
        val user = sessionStore.user ?: return fail(AuthError.NotSignedIn)
        _deletion.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                // The push registration goes first, while the user can still write. If the re-verification below is
                // cancelled it stays removed until the next sign-in registers this install again.
                deviceRegistration.unregister(user.id)
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
        _deletion.update { AccountUiState(isSigningOut = it.isSigningOut) }
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

    /** The re-verification saved before a process death (or none); a step that was running is not resumed. */
    private fun restoredDeletion(): AccountUiState {
        val isReverifying = savedStateHandle[KEY_IS_REVERIFYING] ?: false
        if (!isReverifying || verificationId == null) return AccountUiState()
        return AccountUiState(
            isReverifying = true,
            codeText = savedStateHandle[KEY_CODE_TEXT] ?: "",
            error = savedStateHandle[KEY_ERROR],
            resendAvailableAt = savedStateHandle.get<Long>(KEY_RESEND_AVAILABLE_AT)?.let(Instant::ofEpochMilli),
        )
    }

    /** Mirrors the restorable part of the deletion state into the SavedStateHandle (bundle-safe values only). */
    private fun saveDeletion(state: AccountUiState) {
        savedStateHandle[KEY_IS_REVERIFYING] = state.isReverifying
        savedStateHandle[KEY_CODE_TEXT] = state.codeText
        savedStateHandle[KEY_ERROR] = state.error
        savedStateHandle[KEY_RESEND_AVAILABLE_AT] = state.resendAvailableAt?.toEpochMilli()
    }

    private fun accountState(
        session: SessionState,
        profile: ProfileState,
        wallet: CoinWallet,
        deletion: AccountUiState,
        isAdmin: Boolean,
    ) = deletion.copy(
        profileName = (profile as? ProfileState.Present)?.profile?.fullName,
        coinBalance = wallet.balance,
        formattedPhoneNumber = (session as? SessionState.SignedIn)?.user?.formattedPhoneNumber,
        isAdmin = isAdmin,
    )

    private companion object {
        // SavedStateHandle keys
        const val KEY_IS_REVERIFYING = "account.isReverifying"
        const val KEY_CODE_TEXT = "account.codeText"
        const val KEY_ERROR = "account.error"
        const val KEY_RESEND_AVAILABLE_AT = "account.resendAvailableAt"
        const val KEY_VERIFICATION_ID = "account.verificationId"
    }
}
