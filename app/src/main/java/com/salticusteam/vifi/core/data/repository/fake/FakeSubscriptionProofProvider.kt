package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.SubscriptionProofProvider
import com.salticusteam.vifi.core.model.SubscriptionProof

/** A fixed [SubscriptionProofProvider] for the mock flavor and tests: [proof] (`null` = no subscription). */
class FakeSubscriptionProofProvider(var proof: SubscriptionProof? = null) : SubscriptionProofProvider {

    override suspend fun currentProof(): SubscriptionProof? = proof

    companion object {
        /** A sample proof (never a real token) for a subscribed mock run. */
        val SAMPLE = SubscriptionProof(purchaseToken = "mock-purchase-token", productId = "vifi_premium")
    }
}
