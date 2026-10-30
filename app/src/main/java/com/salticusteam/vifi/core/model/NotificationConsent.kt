package com.salticusteam.vifi.core.model

// Added in 4.1.0 — where a notification prompt or a consent answer comes from; reported as the analytics `source`.
enum class NotificationPromptSource(val raw: String) {
    /** The first offer: the permission flow after the onboarding tour (or its catch-up for existing users). */
    PROMPT("prompt"),

    /** A contextual second offer after an earlier "no" (after a submission, or when the inbox is opened). */
    REASK("reask"),

    /** Account › Bildirim Ayarları. */
    SETTINGS("settings"),
}

// Added in 4.1.0 — where a recorded consent was given: the consent record's `source`, which accepts exactly these.
/** A re-ask is a prompt too. */
enum class NotificationConsentSource(val raw: String) {
    PROMPT("prompt"),
    SETTINGS("settings"),
}

// Added in 4.1.0 — the answer to "may ViFi send you campaigns and student offers?" (`users/{uid}/settings/notifications`).
/**
 * An explicit in-app opt-in, off until given, withdrawable in Account. The server filters campaigns on
 * `marketing == true`, so a user without a record sees none, in the inbox included. No reward is ever given for it.
 */
data class NotificationConsent(val marketing: Boolean) {
    companion object {
        /** The consent wording's version, written with every answer (the rules accept 1…99). */
        const val CURRENT_VERSION = 1
    }
}
