package com.salticusteam.vifi.feature.premium

import com.salticusteam.vifi.core.model.SubscriptionOption

/**
 * The ViFi Premium paywall (iOS PaywallView): the plans, the selected one, and the purchase / restore in flight.
 * While a purchase runs every other action and closing are locked.
 */
data class PaywallUiState(
    /** The plans, monthly first; `null` while loading (and after a failed first load, see [loadFailed]). */
    val options: List<SubscriptionOption>? = null,
    /** The plans could not be read: "Abonelik planları yüklenemedi." + "Tekrar Dene" instead of the cards. */
    val loadFailed: Boolean = false,
    /** The selected plan's base plan id; the yearly plan is chosen once the plans load. */
    val selectedPlanId: String? = null,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    /** The last purchase or restore failure (`SubscriptionError.userMessage`), shown above the button. */
    val error: String? = null,
    /** A non-error outcome shown in the same place: a pending payment, or nothing to restore. */
    val notice: PaywallNotice? = null,
) {
    val selectedOption: SubscriptionOption? get() = options?.firstOrNull { it.basePlanId == selectedPlanId }

    /** "Abone Ol" is enabled: a plan is selected and nothing is running. */
    val canSubscribe: Boolean get() = selectedOption != null && !isBusy

    /** A purchase or a restore is running: the other actions are disabled. */
    val isBusy: Boolean get() = isPurchasing || isRestoring
}

/** The paywall's non-error messages; the screen resolves the text. */
enum class PaywallNotice {
    /** The payment awaits an external action; the subscription starts once it completes. */
    PURCHASE_PENDING,

    /** A restore found no active subscription on this Google account. */
    NOTHING_TO_RESTORE,
}

/** One-off results of the paywall. */
sealed interface PaywallEvent {
    /** A subscription became active (bought, restored or a pending payment completed): the only continuation is closing. */
    data object Subscribed : PaywallEvent
}
