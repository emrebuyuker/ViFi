package com.salticusteam.vifi.feature.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.AuthService
import com.salticusteam.vifi.core.data.repository.LoginDiagnosticsReporter
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.AuthError
import com.salticusteam.vifi.core.model.LoginFailureInfo
import com.salticusteam.vifi.core.model.LoginFailureStage
import com.salticusteam.vifi.core.model.PhoneNumber
import com.salticusteam.vifi.core.model.VerificationCode
import com.salticusteam.vifi.core.model.userMessage
import com.salticusteam.vifi.core.util.trackScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Phone number sign-in: enter the number, then the six-digit code sent by SMS (iOS LoginViewModel).
 *
 * Success needs no handling here: the session gate replaces the login screen once the user is signed in, so the
 * model stays busy until then. That also covers Android's automatic verification, where Play services signs the
 * user in without the code being typed.
 *
 * The step, the typed number and code, the verification id and the resend cooldown live in the
 * [SavedStateHandle], so a configuration change or a process death while the SMS is on its way brings the user
 * back to the same code entry (no iOS counterpart: iOS does not kill a backgrounded app's state this way).
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authService: AuthService,
    private val diagnostics: LoginDiagnosticsReporter,
    private val analytics: AnalyticsTracker,
    /** The clock the resend cooldown is measured with (a fixed or stepping clock in tests). */
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(restoredState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // One-shot events
    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    // Internal state (kept in the SavedStateHandle, so it survives a process death)
    private var verificationId: String?
        get() = savedStateHandle[KEY_VERIFICATION_ID]
        set(value) {
            savedStateHandle[KEY_VERIFICATION_ID] = value
        }

    /** The last code sent, kept across [changeNumber] so re-entering the same number cannot skip the cooldown. */
    private var lastSentCode: SentCode?
        get() {
            val phoneNumber = savedStateHandle.get<String>(KEY_LAST_SENT_NUMBER)?.let(PhoneNumber::fromNational)
            val id = savedStateHandle.get<String>(KEY_LAST_SENT_VERIFICATION_ID)
            val resendAvailableAt = savedStateHandle.get<Long>(KEY_LAST_SENT_RESEND_AT)?.let(Instant::ofEpochMilli)
            if (phoneNumber == null || id == null || resendAvailableAt == null) return null
            return SentCode(phoneNumber, id, resendAvailableAt)
        }
        set(value) {
            savedStateHandle[KEY_LAST_SENT_NUMBER] = value?.phoneNumber?.nationalNumber
            savedStateHandle[KEY_LAST_SENT_VERIFICATION_ID] = value?.verificationId
            savedStateHandle[KEY_LAST_SENT_RESEND_AT] = value?.resendAvailableAt?.toEpochMilli()
        }

    /**
     * The most recent failure's [LoginFailureInfo.supportContext], used to prefill the support e-mail
     * ("Bize Ulaş") with what went wrong.
     */
    private var lastFailureContext: String?
        get() = savedStateHandle[KEY_LAST_FAILURE_CONTEXT]
        set(value) {
            savedStateHandle[KEY_LAST_FAILURE_CONTEXT] = value
        }

    // Intents
    init {
        viewModelScope.launch { _uiState.collect(::saveState) }
        // The login screen lives outside the NavHost, so this model outlives a session and stays busy after a
        // successful sign-in. Start over on sign-out, like iOS presenting a fresh LoginView.
        viewModelScope.launch {
            var wasSignedIn = false
            authService.userChanges().collect { user ->
                if (user == null && wasSignedIn) reset()
                wasSignedIn = user != null
            }
        }
    }

    fun onScreenShown() {
        analytics.trackScreen(AnalyticsScreen.LOGIN)
    }

    /** Takes an edit of the number field, formatted so pasted `+90 5…` or `05…` numbers become `5XX XXX XX XX`. */
    fun onPhoneTextChange(input: String) {
        if (_uiState.value.isWorking) return
        val formatted = PhoneNumber.formattedInput(input)
        _uiState.update { it.copy(phoneText = formatted, error = if (formatted.isEmpty()) it.error else null) }
    }

    /** Takes an edit of the code field: digits only, at most six, submitted once all six are there. */
    fun onCodeChange(input: String) {
        if (_uiState.value.isWorking) return
        val digits = VerificationCode.digits(input)
        _uiState.update { it.copy(codeText = digits, error = if (digits.isEmpty()) it.error else null) }
        if (digits.length == VerificationCode.LENGTH) verifyCode()
    }

    /**
     * Sends a code to the typed number. Within the cooldown of a code already sent to the same number, that code's
     * entry is shown again instead of sending another SMS.
     */
    fun sendCode() {
        val state = _uiState.value
        val phoneNumber = state.phoneNumber
        if (state.isWorking || phoneNumber == null) return
        val sent = lastSentCode
        if (sent != null && sent.phoneNumber == phoneNumber && clock.instant() < sent.resendAvailableAt) {
            showCodeStep(sent)
            return
        }
        requestCode(phoneNumber, isResend = false)
    }

    /** Sends a new code to the same number once the cooldown is over. */
    fun resendCode() {
        val step = _uiState.value.step as? LoginStep.Code ?: return
        if (!canResendCode()) return
        requestCode(step.phoneNumber, isResend = true)
    }

    /** Signs in with the entered code. */
    fun verifyCode() {
        val id = verificationId
        val state = _uiState.value
        if (!state.canVerify || id == null) return
        _uiState.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                authService.signIn(id, state.codeText)
                analytics.track(AnalyticsEvent.LoginSucceeded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e, LoginFailureStage.VERIFY)
            }
        }
    }

    /** Back to the number step, keeping the typed number for editing. */
    fun changeNumber() {
        if (_uiState.value.isWorking) return
        verificationId = null
        _uiState.update {
            it.copy(step = LoginStep.Phone, codeText = "", error = null, resendAvailableAt = null)
        }
    }

    /** "Bize Ulaş": opens the support e-mail with the last failure's details, or the typed number. */
    fun onContactSupport() {
        viewModelScope.launch { _events.send(LoginEvent.ContactSupport(supportContext() ?: LOGIN_SCREEN_CONTEXT)) }
    }

    /** Whole seconds left until another code can be requested, `0` once it can. */
    fun resendCooldownRemaining(at: Instant = clock.instant()): Int {
        val availableAt = _uiState.value.resendAvailableAt ?: return 0
        val millis = Duration.between(at, availableAt).toMillis()
        return if (millis <= 0) 0 else ((millis + 999) / 1000).toInt()
    }

    fun canResendCode(at: Instant = clock.instant()): Boolean {
        val state = _uiState.value
        return state.step is LoginStep.Code && !state.isWorking && resendCooldownRemaining(at) == 0
    }

    /**
     * Prefilled into the support e-mail: the last failure's details when there is one, otherwise just the typed
     * number; `null` when there is nothing useful to add.
     */
    fun supportContext(): String? {
        lastFailureContext?.let { return it }
        val phoneNumber = _uiState.value.phoneNumber ?: return null
        return "Giriş sorunu bildirimi\nNumara: ${phoneNumber.e164}"
    }

    // Helpers
    /** Back to an empty number step, forgetting the finished session's code and failure. */
    private fun reset() {
        verificationId = null
        lastSentCode = null
        lastFailureContext = null
        _uiState.value = LoginUiState()
    }

    private fun requestCode(phoneNumber: PhoneNumber, isResend: Boolean) {
        _uiState.update { it.copy(isWorking = true, error = null) }
        analytics.track(AnalyticsEvent.LoginCodeRequested(isResend))
        viewModelScope.launch {
            try {
                val id = authService.sendVerificationCode(phoneNumber.e164)
                // Play services verified the number and signed the user in without a code (Android only).
                if (id == AuthService.INSTANT_VERIFICATION_ID) analytics.track(AnalyticsEvent.LoginSucceeded)
                didSendCode(phoneNumber, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e, LoginFailureStage.SEND_CODE)
            }
        }
    }

    private fun didSendCode(phoneNumber: PhoneNumber, id: String) {
        val sent = SentCode(phoneNumber, id, clock.instant().plus(RESEND_COOLDOWN))
        lastSentCode = sent
        showCodeStep(sent)
    }

    private fun showCodeStep(sent: SentCode) {
        verificationId = sent.verificationId
        _uiState.update {
            it.copy(
                step = LoginStep.Code(sent.phoneNumber),
                codeText = "",
                isWorking = false,
                error = null,
                resendAvailableAt = sent.resendAvailableAt,
            )
        }
    }

    private fun fail(error: Throwable, stage: LoginFailureStage) {
        val authError = error as? AuthError
        val info = LoginFailureInfo(
            reason = authError?.analyticsReason ?: "unknown",
            backendCode = authError?.let(::backendCode),
            stage = stage,
            phoneNumber = attemptedNumber()?.e164,
            occurredAt = clock.instant(),
        )
        lastFailureContext = info.supportContext
        analytics.track(AnalyticsEvent.LoginFailed(reason = info.reason, step = stage.raw))
        // Fire-and-forget: the reporter throttles duplicates and never blocks or surfaces.
        diagnostics.report(info)

        _uiState.update { state ->
            val failed = state.copy(isWorking = false, error = error.userMessage)
            when (authError) {
                // Ready for the next attempt; the message stays until the user types again.
                AuthError.InvalidCode -> failed.copy(codeText = "")
                // The code is useless now; a new one may be requested right away.
                AuthError.CodeExpired -> failed.copy(codeText = "", resendAvailableAt = null)
                else -> failed
            }
        }
        if (authError == AuthError.CodeExpired) lastSentCode = null
    }

    /** The state saved before a process death (or a fresh one); a request that was running is not resumed. */
    private fun restoredState(): LoginUiState {
        val codeNumber = savedStateHandle.get<String>(KEY_CODE_STEP_NUMBER)?.let(PhoneNumber::fromNational)
        val step = if (codeNumber != null && verificationId != null) LoginStep.Code(codeNumber) else LoginStep.Phone
        return LoginUiState(
            step = step,
            phoneText = savedStateHandle[KEY_PHONE_TEXT] ?: "",
            codeText = if (step is LoginStep.Code) savedStateHandle[KEY_CODE_TEXT] ?: "" else "",
            error = savedStateHandle[KEY_ERROR],
            resendAvailableAt = savedStateHandle.get<Long>(KEY_RESEND_AVAILABLE_AT)?.let(Instant::ofEpochMilli),
        )
    }

    /** Mirrors the restorable part of [state] into the SavedStateHandle (bundle-safe values only). */
    private fun saveState(state: LoginUiState) {
        savedStateHandle[KEY_PHONE_TEXT] = state.phoneText
        savedStateHandle[KEY_CODE_STEP_NUMBER] = (state.step as? LoginStep.Code)?.phoneNumber?.nationalNumber
        savedStateHandle[KEY_CODE_TEXT] = state.codeText
        savedStateHandle[KEY_ERROR] = state.error
        savedStateHandle[KEY_RESEND_AVAILABLE_AT] = state.resendAvailableAt?.toEpochMilli()
    }

    /** The number the failed attempt used: the one on the code step, otherwise the number being typed. */
    private fun attemptedNumber(): PhoneNumber? {
        val state = _uiState.value
        return (state.step as? LoginStep.Code)?.phoneNumber ?: state.phoneNumber
    }

    /** The backend code behind an error, when known (39 for the SMS anti-abuse rejection). */
    private fun backendCode(error: AuthError): Int? = when (error) {
        AuthError.SmsTemporarilyUnavailable -> SMS_ANTI_ABUSE_BACKEND_CODE
        else -> null
    }

    /** A code sent by SMS, and when another may be requested for the same number. */
    private data class SentCode(
        val phoneNumber: PhoneNumber,
        val verificationId: String,
        val resendAvailableAt: Instant,
    )

    companion object {
        /** How long before another code can be requested for the same number. */
        val RESEND_COOLDOWN: Duration = Duration.ofSeconds(60)

        private const val SMS_ANTI_ABUSE_BACKEND_CODE = 39

        /** Support e-mail context when nothing more specific is known (iOS "Giriş ekranı"). */
        private const val LOGIN_SCREEN_CONTEXT = "Giriş ekranı"

        // SavedStateHandle keys
        private const val KEY_PHONE_TEXT = "login.phoneText"
        private const val KEY_CODE_STEP_NUMBER = "login.codeStepNumber"
        private const val KEY_CODE_TEXT = "login.codeText"
        private const val KEY_ERROR = "login.error"
        private const val KEY_RESEND_AVAILABLE_AT = "login.resendAvailableAt"
        private const val KEY_VERIFICATION_ID = "login.verificationId"
        private const val KEY_LAST_SENT_NUMBER = "login.lastSent.number"
        private const val KEY_LAST_SENT_VERIFICATION_ID = "login.lastSent.verificationId"
        private const val KEY_LAST_SENT_RESEND_AT = "login.lastSent.resendAvailableAt"
        private const val KEY_LAST_FAILURE_CONTEXT = "login.lastFailureContext"
    }
}
