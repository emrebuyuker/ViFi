package com.salticusteam.vifi.core.data.billing

import com.salticusteam.vifi.core.data.repository.SubscriptionProofProvider
import com.salticusteam.vifi.core.model.SubscriptionProof
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Premium proof of the live flavor (4.2.0 PRC-03): the active Play subscription, read again on every call (iOS
 * `currentEntitlementJWS`). `null` without a subscription, which means the free rules.
 */
@Singleton
class PlaySubscriptionProofProvider @Inject constructor(
    private val subscription: PlayBillingSubscriptionService,
) : SubscriptionProofProvider {

    override suspend fun currentProof(): SubscriptionProof? = subscription.currentProof()
}
