package com.salticusteam.vifi.core.data.coins

import com.salticusteam.vifi.core.data.repository.WalletReader
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.CoinWallet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * The signed-in user's coin balance, shared app-wide so every top bar shows the same live value (iOS
 * CoinWalletStore, driven there by RootView's `.task(id: uid)` / `reset()`).
 *
 * Follows [SessionStore]: a new uid swaps the wallet listener (the old one is removed) and starts from
 * [CoinWallet.EMPTY], so user B never sees user A's balance; sign-out drops back to empty. A single listener is
 * shared by all collectors and closed a few seconds after the last one leaves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CoinWalletStore @Inject constructor(
    reader: WalletReader,
    sessionStore: SessionStore,
    @ApplicationScope scope: CoroutineScope,
) {

    // State
    val wallet: StateFlow<CoinWallet> = sessionStore.state
        .map { (it as? SessionState.SignedIn)?.user?.id }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(CoinWallet.EMPTY) else reader.walletChanges(uid).onStart { emit(CoinWallet.EMPTY) }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CoinWallet.EMPTY)

    /** The current spendable balance. */
    val balance: Int get() = wallet.value.balance

    private companion object {
        /** Keeps the listener across a configuration change or a quick back-and-forth between screens. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
