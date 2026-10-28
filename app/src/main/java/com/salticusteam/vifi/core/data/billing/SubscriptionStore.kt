package com.salticusteam.vifi.core.data.billing

import com.salticusteam.vifi.core.data.repository.SubscriptionService
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.SubscriptionOption
import com.salticusteam.vifi.core.util.LogCategory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * The device's Premium state, shared app-wide so the archive can drop every lock and the UI can hide the coin
 * economy while a subscription is active (iOS SubscriptionStore).
 *
 * Unlike the wallet or the unlocks, this is tied to the Google account, not the signed-in user: it follows
 * [SubscriptionService.statusChanges] for the app's lifetime from its creation and is never reset on sign-out.
 * [isSubscribed] starts `false` — locked is the safe default — until the first read. [loadOptions] is called lazily
 * by the paywall.
 */
@Singleton
class SubscriptionStore @Inject constructor(
    private val service: SubscriptionService,
    @ApplicationScope scope: CoroutineScope,
) {

    // State
    private val _isSubscribed = MutableStateFlow(false)

    /** Whether an active subscription unlocks the whole archive. */
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _options = MutableStateFlow<List<SubscriptionOption>>(emptyList())

    /** The paywall's plans, empty until [loadOptions] succeeds. */
    val options: StateFlow<List<SubscriptionOption>> = _options.asStateFlow()

    // Intents
    init {
        scope.launch {
            service.statusChanges()
                // A failed read keeps the last known state (locked before the first one).
                .catch { e -> LogCategory.APP.log.w(e, "Could not follow the subscription state") }
                .collect { _isSubscribed.value = it }
        }
    }

    /**
     * Loads the paywall's plans. A failure leaves the current list; the caller shows the message.
     *
     * @throws com.salticusteam.vifi.core.model.SubscriptionError
     */
    suspend fun loadOptions() {
        _options.value = service.subscriptionOptions()
    }

    /**
     * Buys [option]. On success the entitlement is re-read at once, so the app does not wait for the next status
     * update to drop the locks.
     *
     * @throws com.salticusteam.vifi.core.model.SubscriptionError
     */
    suspend fun purchase(option: SubscriptionOption): PurchaseResult {
        val result = service.purchase(option)
        if (result == PurchaseResult.SUCCESS) _isSubscribed.value = service.isSubscribed()
        return result
    }

    /**
     * Restores past purchases (a reinstall or a new device), then re-reads the entitlement.
     *
     * @throws com.salticusteam.vifi.core.model.SubscriptionError
     */
    suspend fun restore() {
        service.restore()
        _isSubscribed.value = service.isSubscribed()
    }
}
