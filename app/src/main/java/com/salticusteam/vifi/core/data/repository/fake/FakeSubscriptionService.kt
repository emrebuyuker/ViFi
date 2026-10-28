package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.SubscriptionService
import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.StoreProductIds
import com.salticusteam.vifi.core.model.SubscriptionError
import com.salticusteam.vifi.core.model.SubscriptionOption
import com.salticusteam.vifi.core.model.SubscriptionPeriod
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory Premium subscription for the mock flavor, previews and tests (iOS MockSubscriptionService): no Google
 * Play. Starts not subscribed unless [subscribed]; a [PurchaseResult.SUCCESS] purchase subscribes.
 */
class FakeSubscriptionService(
    subscribed: Boolean = false,
    private val latency: Duration = 300.milliseconds,
    private val failure: SubscriptionError? = null,
    private val purchaseResult: PurchaseResult = PurchaseResult.SUCCESS,
    private val options: List<SubscriptionOption> = SAMPLE_OPTIONS,
) : SubscriptionService {

    private val state = MutableStateFlow(subscribed)

    private val _purchased = mutableListOf<SubscriptionOption>()

    /** The plans bought so far, in order. */
    val purchased: List<SubscriptionOption> get() = _purchased

    /** How many times [restore] completed. */
    var restoreCount = 0
        private set

    override suspend fun subscriptionOptions(): List<SubscriptionOption> {
        delay(latency)
        failure?.let { throw it }
        return options
    }

    override suspend fun isSubscribed(): Boolean = state.value

    override fun statusChanges(): Flow<Boolean> = state.asStateFlow()

    override suspend fun purchase(option: SubscriptionOption): PurchaseResult {
        delay(latency)
        failure?.let { throw it }
        _purchased += option
        if (purchaseResult == PurchaseResult.SUCCESS) state.value = true
        return purchaseResult
    }

    override suspend fun restore() {
        delay(latency)
        failure?.let { throw it }
        restoreCount++
    }

    /** Changes the state from outside, like a lapsed renewal or a purchase on another device. */
    fun setSubscribed(subscribed: Boolean) {
        state.value = subscribed
    }

    companion object {
        /** The two Premium plans at the 4.0.0 prices (DEC-02), monthly first. */
        val SAMPLE_OPTIONS = listOf(
            SubscriptionOption(
                basePlanId = StoreProductIds.MONTHLY_PLAN,
                offerToken = "fake-monthly",
                displayPrice = "₺59,99",
                period = SubscriptionPeriod.MONTHLY,
            ),
            SubscriptionOption(
                basePlanId = StoreProductIds.YEARLY_PLAN,
                offerToken = "fake-yearly",
                displayPrice = "₺399,99",
                period = SubscriptionPeriod.YEARLY,
            ),
        )
    }
}
