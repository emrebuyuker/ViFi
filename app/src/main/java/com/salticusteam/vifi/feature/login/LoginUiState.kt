package com.salticusteam.vifi.feature.login

import com.salticusteam.vifi.core.model.PhoneNumber
import com.salticusteam.vifi.core.model.VerificationCode
import java.time.Instant

/** The two steps of phone sign-in. */
sealed interface LoginStep {
    /** Typing the number. */
    data object Phone : LoginStep

    /** A code was sent to [phoneNumber]; typing it. */
    data class Code(val phoneNumber: PhoneNumber) : LoginStep
}

/** The login screen's single state (iOS LoginViewModel's observable properties). */
data class LoginUiState(
    val step: LoginStep = LoginStep.Phone,
    /** The number as typed, formatted as `5XX XXX XX XX`. */
    val phoneText: String = "",
    /** The code as typed; digits only, at most six. */
    val codeText: String = "",
    val isWorking: Boolean = false,
    /** A domain error's Turkish `userMessage`, shown under the field. */
    val error: String? = null,
    /** When the cooldown after the last sent code ends; `null` when another code may be requested now. */
    val resendAvailableAt: Instant? = null,
) {
    /** The typed number, once it is a complete Turkish mobile number. */
    val phoneNumber: PhoneNumber? get() = PhoneNumber.fromInput(phoneText)

    val canSendCode: Boolean get() = phoneNumber != null && !isWorking

    /** Only the code step has a verification id, so being on it is enough. */
    val canVerify: Boolean
        get() = step is LoginStep.Code && codeText.length == VerificationCode.LENGTH && !isWorking
}

/** One-shot effects of the login screen. */
sealed interface LoginEvent {
    /** Open the support e-mail ("Bize Ulaş"), prefilled with [context]. */
    data class ContactSupport(val context: String) : LoginEvent
}
