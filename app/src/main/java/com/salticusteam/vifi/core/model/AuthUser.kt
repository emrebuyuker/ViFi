package com.salticusteam.vifi.core.model

// Added in 2.5 — the signed-in user, as the app sees it.
data class AuthUser(
    /** The Firebase user id (`auth.uid` in security rules). */
    val id: String,
    /** The verified number in E.164 format (`+905321234567`); `null` only for accounts without one. */
    val phoneNumber: String?,
) {
    /** `+90 532 123 45 67`, or the raw value when it is not a Turkish number. */
    val formattedPhoneNumber: String?
        get() = phoneNumber?.let { PhoneNumber.fromE164(it)?.formatted ?: it }
}
