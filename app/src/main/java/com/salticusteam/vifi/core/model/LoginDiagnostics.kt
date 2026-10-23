package com.salticusteam.vifi.core.model

import java.time.Duration
import java.time.Instant

// Added in 2.5 — which stage of sign-in failed; stable raw values shared by analytics and the diagnostics sink.
enum class LoginFailureStage(val raw: String) {
    /** Requesting the SMS code (`sendVerificationCode`): where Error 39 and App Check rejections surface. */
    SEND_CODE("send_code"),

    /** Verifying the entered code (`signIn`). */
    VERIFY("verify");

    /** Turkish label embedded in the support e-mail body (not UI chrome, so it lives with the model, as on iOS). */
    val turkishLabel: String
        get() = when (this) {
            SEND_CODE -> "Kod gönderme"
            VERIFY -> "Kod doğrulama"
        }
}

// Added in 2.5 — a structured record of a failed sign-in attempt, built with no Firebase types.
/** The live reporter enriches it (App Check status, device, version) before sending it to the diagnostics sink. */
data class LoginFailureInfo(
    /** The stable analytics reason (`AuthError.analyticsReason`), e.g. `sms_temporarily_unavailable`. */
    val reason: String,
    /** The backend code behind the failure when known (39 for the SMS anti-abuse rejection), else `null`. */
    val backendCode: Int?,
    val stage: LoginFailureStage,
    /** The attempted number in E.164 (`+90...`), or `null` when it is not known. */
    val phoneNumber: String?,
    val occurredAt: Instant,
) {
    /**
     * A short, human-readable Turkish summary prefilled into the support e-mail. It carries the full number
     * because the user sends the mail themselves; nothing is hashed here.
     */
    val supportContext: String
        get() {
            val lines = mutableListOf("Giriş sorunu bildirimi")
            if (phoneNumber != null) lines += "Numara: $phoneNumber"
            lines += "Aşama: ${stage.turkishLabel}"
            val code = backendCode?.let { " (Error code: $it)" }.orEmpty()
            lines += "Sebep: $reason$code"
            return lines.joinToString("\n")
        }
}

// Added in 2.5 — decides whether a diagnostic should actually be sent, so identical failures do not spam the backend.
/**
 * A report is sent when its signature differs from the last sent one, or when [window] has elapsed since the
 * last send of the same signature. Pure and synchronous, so it is unit-tested directly.
 */
class LoginDiagnosticThrottle(
    /** How long an identical report stays suppressed after one is sent. */
    private val window: Duration = Duration.ofSeconds(60),
) {
    private var lastSignature: String? = null
    private var lastSentAt: Instant? = null

    /**
     * Records and allows the report when it should be sent; returns `false` (changing nothing) when it is a
     * duplicate inside the window.
     */
    fun shouldSend(signature: String, at: Instant): Boolean {
        val sentAt = lastSentAt
        if (lastSignature == signature && sentAt != null && Duration.between(sentAt, at) < window) {
            return false
        }
        lastSignature = signature
        lastSentAt = at
        return true
    }
}
