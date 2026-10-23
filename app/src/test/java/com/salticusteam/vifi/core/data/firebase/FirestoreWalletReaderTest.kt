package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.Timestamp
import com.salticusteam.vifi.core.model.CoinWallet
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FirestoreWalletReaderTest {

    @Test
    fun `parse reads balance and updatedAt`() {
        val wallet = FirestoreWalletReader.parse(mapOf("balance" to 250L, "updatedAt" to Timestamp(1_700_000_000L, 5)))

        assertEquals(CoinWallet(balance = 250, updatedAt = Instant.ofEpochSecond(1_700_000_000L, 5)), wallet)
    }

    @Test
    fun `parse when document missing returns empty`() {
        assertEquals(CoinWallet.EMPTY, FirestoreWalletReader.parse(null))
    }

    @Test
    fun `parse when balance negative or missing clamps to zero`() {
        assertEquals(0, FirestoreWalletReader.parse(mapOf("balance" to -40L)).balance)
        assertEquals(0, FirestoreWalletReader.parse(mapOf("balance" to "lots")).balance)
        assertEquals(CoinWallet(balance = 0, updatedAt = null), FirestoreWalletReader.parse(emptyMap()))
    }
}
