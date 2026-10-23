package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsUser

/** Reports analytics events and the signed-in user's identity. */
interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)

    /** Identifies the signed-in user (id + segment properties), or clears the identity with `null`. */
    fun setUser(user: AnalyticsUser?)
}
