package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.salticusteam.vifi.core.data.repository.WalletReader
import com.salticusteam.vifi.core.model.CoinWallet
import com.salticusteam.vifi.core.util.LogCategory
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Reads a user's coin wallet from the named 'vifi' Firestore `wallets/<uid>` document with a live snapshot
 * listener (iOS `FirebaseWalletReader`). The snapshot is parsed inside the callback and never leaves it; the
 * owner may read their own wallet, and App Check guards the read. Only the server writes it.
 */
class FirestoreWalletReader @Inject constructor(
    private val firestore: FirebaseFirestore,
) : WalletReader {

    override fun walletChanges(uid: String): Flow<CoinWallet> = callbackFlow {
        val registration = firestore.collection(COLLECTION).document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                // A rejected read (rules / App Check) shows a zero balance rather than ending the stream (iOS).
                LogCategory.APP.log.e(error, "Wallet listener failed")
                trySend(CoinWallet.EMPTY)
                return@addSnapshotListener
            }
            trySend(parse(snapshot?.data))
        }
        awaitClose { registration.remove() }
    }.conflate()

    internal companion object {
        const val COLLECTION = "wallets"

        /**
         * Parses the wallet document. A missing document, missing fields or a negative balance read as empty /
         * zero; Firestore integers arrive as `Long`.
         */
        fun parse(data: Map<String, Any?>?): CoinWallet {
            if (data == null) return CoinWallet.EMPTY
            val balance = (data["balance"] as? Number)?.toInt() ?: 0
            val updatedAt = (data["updatedAt"] as? Timestamp)
                ?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) }
            return CoinWallet(balance = balance.coerceAtLeast(0), updatedAt = updatedAt)
        }
    }
}
