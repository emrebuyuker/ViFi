package com.salticusteam.vifi.feature.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.billing.SubscriptionStore
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.SubscriptionOption
import com.salticusteam.vifi.core.model.SubscriptionPeriod
import com.salticusteam.vifi.core.model.userMessage
import com.salticusteam.vifi.core.util.trackScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The ViFi Premium paywall (iOS PaywallView): loads the plans (yearly selected), buys one, restores past purchases,
 * and asks to close as soon as a subscription is active — whether bought here, restored, or a pending payment that
 * completed. The purchase sheet's Activity is found by the billing layer, so nothing here holds one.
 */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val subscriptionStore: SubscriptionStore,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(
        // Plans read earlier show at once; they are refreshed below.
        initialState(subscriptionStore.options.value.takeIf { it.isNotEmpty() }),
    )
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    // One-shot events
    private val _events = Channel<PaywallEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Internal state
    private var loadJob: Job? = null

    // Intents
    init {
        viewModelScope.launch {
            // The store is the single source of the entitlement (iOS `onChange(of: isSubscribed)`).
            subscriptionStore.isSubscribed.first { it }
            _events.send(PaywallEvent.Subscribed)
        }
        loadOptions()
    }

    fun onScreenShown() {
        analytics.trackScreen(AnalyticsScreen.PAYWALL)
    }

    /** "Tekrar Dene" after a failed load. */
    fun onRetry() = loadOptions()

    fun onPlanSelected(basePlanId: String) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(selectedPlanId = basePlanId) }
    }

    /** Buys the selected plan; a success closes the paywall through the entitlement, a cancel just stays. */
    fun onSubscribe() {
        val state = _uiState.value
        val option = state.selectedOption ?: return
        if (state.isBusy) return

        _uiState.update { it.copy(isPurchasing = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                val notice = when (subscriptionStore.purchase(option)) {
                    // The store re-reads the entitlement; the collector in init closes the paywall.
                    PurchaseResult.SUCCESS -> null
                    PurchaseResult.CANCELLED -> null
                    PurchaseResult.PENDING -> PaywallNotice.PURCHASE_PENDING
                }
                _uiState.update { it.copy(isPurchasing = false, notice = notice) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isPurchasing = false, error = e.userMessage) }
            }
        }
    }

    /** Re-reads past purchases from Google Play; says so when there is no subscription to restore. */
    fun onRestore() {
        if (_uiState.value.isBusy) return

        _uiState.update { it.copy(isRestoring = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                subscriptionStore.restore()
                val notice = PaywallNotice.NOTHING_TO_RESTORE.takeUnless { subscriptionStore.isSubscribed.value }
                _uiState.update { it.copy(isRestoring = false, notice = notice) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isRestoring = false, error = e.userMessage) }
            }
        }
    }

    // Helpers
    private fun loadOptions() {
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(loadFailed = false) }
        loadJob = viewModelScope.launch {
            try {
                subscriptionStore.loadOptions()
                val loaded = subscriptionStore.options.value
                if (loaded.isEmpty()) {
                    // Nothing to buy would leave a dead screen; offer the retry instead.
                    _uiState.update { it.copy(loadFailed = it.options == null) }
                } else {
                    _uiState.update { it.copy(options = loaded, selectedPlanId = it.selectedPlanId ?: defaultPlanId(loaded)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Plans already shown stay; only a first load that failed offers the retry (iOS `loadFailed`).
                _uiState.update { it.copy(loadFailed = it.options == null) }
            }
        }
    }

    private companion object {
        fun initialState(options: List<SubscriptionOption>?) =
            PaywallUiState(options = options, selectedPlanId = options?.let(::defaultPlanId))

        /** The yearly plan (best value), else the first one (iOS PaywallView). */
        fun defaultPlanId(options: List<SubscriptionOption>): String? =
            (options.firstOrNull { it.period == SubscriptionPeriod.YEARLY } ?: options.firstOrNull())?.basePlanId
    }
}
