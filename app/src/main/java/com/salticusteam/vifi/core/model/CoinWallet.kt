package com.salticusteam.vifi.core.model

import java.time.Instant

// Added in 2.5 — a user's coin balance, read from `wallets/<uid>`; server-authoritative, never written by the client.
data class CoinWallet(
    /** The current spendable balance; never negative. */
    val balance: Int,
    /** When the balance last changed (server timestamp), if known. */
    val updatedAt: Instant? = null,
) {
    companion object {
        /** The balance of a user who has not earned any coins yet (no wallet document). */
        val EMPTY = CoinWallet(balance = 0, updatedAt = null)
    }
}
