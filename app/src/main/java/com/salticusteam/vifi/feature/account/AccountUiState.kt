package com.salticusteam.vifi.feature.account

import com.salticusteam.vifi.core.model.VerificationCode
import java.time.Instant

/**
 * The account screen (iOS AccountView + AccountViewModel): who is signed in, for the profile, Premium, coin and phone
 * rows, and the account deletion with its re-verification of the number.
 */
data class AccountUiState(
    /** The profile's `Ad Soyad`, or `null` when no profile is loaded (the row then offers to edit it). */
    val profileName: String? = null,
    /** `+90 532 123 45 67`, or `null` for an account without a number. */
    val formattedPhoneNumber: String? = null,
    /** A deletion step is running; every control is disabled meanwhile. */
    val isWorking: Boolean = false,
    /** The last failure's message, shown under the rows. */
    val error: String? = null,
    /** The number is being verified again before deletion (the code entry is shown). */
    val isReverifying: Boolean = false,
    /** The re-verification code as typed; digits only, at most six. */
    val codeText: String = "",
    /** When the cooldown after the last re-verification code ends; `null` when another may be sent now. */
    val resendAvailableAt: Instant? = null,
    /** The user carries the `admin` claim: the Moderasyon entry is shown. */
    val isAdmin: Boolean = false,
    /** The live spendable coin balance (`wallets/<uid>`), `0` until the wallet is read. */
    val coinBalance: Int = 0,
    /** An active Premium subscription: the active status and "Aboneliği Yönet" replace the upgrade row; no coin row. */
    val isSubscribed: Boolean = false,
) {
    /** "Doğrula ve Hesabı Sil" is enabled: all six digits are there and nothing is running. */
    val canConfirmReverification: Boolean
        get() = isReverifying && codeText.length == VerificationCode.LENGTH && !isWorking
}
