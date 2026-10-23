package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.CoinWallet
import kotlinx.coroutines.flow.Flow

/** Reads a user's coin wallet from the backend with a live listener. */
interface WalletReader {
    /**
     * A live stream of the wallet for [uid]: the current balance right away, then again on every change. Emits
     * [CoinWallet.EMPTY] when there is no wallet document yet or the read is rejected. Collection ends when
     * the collector is cancelled.
     */
    fun walletChanges(uid: String): Flow<CoinWallet>
}
