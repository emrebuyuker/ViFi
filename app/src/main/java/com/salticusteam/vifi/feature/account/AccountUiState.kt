package com.salticusteam.vifi.feature.account

/** The account screen (iOS AccountView): who is signed in, for the profile and phone rows. */
data class AccountUiState(
    /** The profile's `Ad Soyad`, or `null` when no profile is loaded (the row then offers to edit it). */
    val profileName: String? = null,
    /** `+90 532 123 45 67`, or `null` for an account without a number. */
    val formattedPhoneNumber: String? = null,
)
