package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.SubscriptionOption
import kotlinx.coroutines.flow.Flow

/**
 * The ViFi Premium subscription, backed by Google Play Billing (iOS SubscriptionServicing).
 *
 * The entitlement is read from the device's Play purchases (per Google account), independent of Firebase sign-in:
 * an active subscription unlocks the whole archive and hides the coin economy. A purchase needs the foreground
 * Activity, which the implementation obtains itself, so callers (ViewModels) never hold one. The live
 * implementation is Play Billing; `FakeSubscriptionService` backs the mock flavor, previews and tests.
 */
interface SubscriptionService {
    /**
     * The Premium plans to show on the paywall, monthly first.
     *
     * @throws com.salticusteam.vifi.core.model.SubscriptionError when Google Play cannot be reached or has no product.
     */
    suspend fun subscriptionOptions(): List<SubscriptionOption>

    /** Whether the device currently holds an active (purchased, not expired or refunded) Premium subscription. */
    suspend fun isSubscribed(): Boolean

    /**
     * Emits the current subscription state at once, then again on every change — a purchase, renewal, expiry,
     * refund, or a purchase made on another device.
     */
    fun statusChanges(): Flow<Boolean>

    /**
     * Buys [option] through the Google Play purchase sheet.
     *
     * @throws com.salticusteam.vifi.core.model.SubscriptionError when Google Play fails.
     */
    suspend fun purchase(option: SubscriptionOption): PurchaseResult

    /**
     * Re-reads past purchases from Google Play (a reinstall or a new device).
     *
     * @throws com.salticusteam.vifi.core.model.SubscriptionError when Google Play cannot be reached.
     */
    suspend fun restore()
}
