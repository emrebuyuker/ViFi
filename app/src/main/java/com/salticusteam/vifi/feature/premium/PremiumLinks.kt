package com.salticusteam.vifi.feature.premium

import com.salticusteam.vifi.core.model.StoreProductIds

/** Where the user manages ViFi Premium outside the app (iOS `PremiumLegal.manageSubscriptionsURL`). */
object PremiumLinks {

    /** Google Play's page for this subscription: change the plan, update the payment method, or cancel. */
    const val MANAGE_SUBSCRIPTIONS_URL =
        "https://play.google.com/store/account/subscriptions?sku=${StoreProductIds.PREMIUM}&package=com.salticusteam.vifi"
}
