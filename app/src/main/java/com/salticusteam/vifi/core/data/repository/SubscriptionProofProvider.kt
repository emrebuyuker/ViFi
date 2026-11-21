package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.SubscriptionProof

/**
 * The Premium proof of the device's active Play subscription (4.2.0, iOS `currentEntitlementJWS`), sent with every
 * practice call. `null` without an active subscription, which means the free rules (never an error). Live: Play
 * Billing (PRC-03); until then no proof is sent.
 */
interface SubscriptionProofProvider {
    suspend fun currentProof(): SubscriptionProof?
}
