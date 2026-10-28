package com.salticusteam.vifi.core.model

// Added in 4.0.0 — the Google Play product identifiers of ViFi Premium (4.0.0 DEC-01).
/**
 * The Play product and base plan ids, kept in one place so the billing layer, the paywall and the server
 * (`purchaseExamUnlock`) agree (iOS `StoreProductID`). They must match Play Console exactly and can never be
 * renamed once created.
 */
object StoreProductIds {
    /** The auto-renewing subscription that unlocks the whole archive; its plans are [MONTHLY_PLAN] and [YEARLY_PLAN]. */
    const val PREMIUM = "vifi_premium"

    /** The monthly base plan of [PREMIUM]. */
    const val MONTHLY_PLAN = "monthly"

    /** The yearly base plan of [PREMIUM]. */
    const val YEARLY_PLAN = "yearly"

    /** One-time consumable: unlocks a single exam with money instead of coins (recorded server-side). */
    const val EXAM_UNLOCK = "exam_unlock"
}

// Added in 4.0.0 — the billing period of a Premium plan (iOS `SubscriptionPeriod`).
/** Lets the paywall label a plan and list the monthly one first. */
enum class SubscriptionPeriod {
    MONTHLY,
    YEARLY,
    ;

    companion object {
        /** The period of a Premium base plan, `null` for a plan id the app does not recognise. */
        fun forBasePlanId(basePlanId: String): SubscriptionPeriod? = when (basePlanId) {
            StoreProductIds.MONTHLY_PLAN -> MONTHLY
            StoreProductIds.YEARLY_PLAN -> YEARLY
            else -> null
        }
    }
}

// Added in 4.0.0 — a Premium plan the user can buy (iOS `SubscriptionOption`).
/**
 * Flattened from Play's product details, so the paywall is free of Billing types (previewable with sample data,
 * unit-testable). [basePlanId] identifies the plan within [StoreProductIds.PREMIUM]; [offerToken] is the opaque
 * token Play needs to buy it; [displayPrice] is Play's localized price, e.g. "₺399,99".
 */
data class SubscriptionOption(
    val basePlanId: String,
    val offerToken: String,
    val displayPrice: String,
    val period: SubscriptionPeriod,
)

// Added in 4.0.0 — the outcome of a purchase attempt (iOS `PurchaseResult`).
/** A cancel or a pending payment is not an error; failures are thrown as [SubscriptionError]. */
enum class PurchaseResult {
    /** The purchase completed: the subscription is active, or the paid exam unlock is recorded. */
    SUCCESS,

    /** The user dismissed the Google Play purchase sheet. */
    CANCELLED,

    /** The payment awaits an external action (e.g. a cash payment); it may complete later. */
    PENDING,
}
