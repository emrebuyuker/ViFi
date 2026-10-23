package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.WalletReader
import com.salticusteam.vifi.core.model.CoinWallet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A wallet held in memory for the mock flavor, previews and tests (iOS StubWalletReader). */
class FakeWalletReader(balance: Int = SAMPLE_BALANCE) : WalletReader {

    /** The wallet served to every uid; set it to simulate a server-side change. */
    val wallet = MutableStateFlow(CoinWallet(balance = balance))

    /** Every uid a stream was opened for, in order (for tests). */
    val observedUids = mutableListOf<String>()

    override fun walletChanges(uid: String): Flow<CoinWallet> {
        observedUids += uid
        return wallet
    }

    companion object {
        /** Enough for one unlock (iOS mock parity); `vifi_no_coins` starts at zero instead. */
        const val SAMPLE_BALANCE = 120
    }
}
