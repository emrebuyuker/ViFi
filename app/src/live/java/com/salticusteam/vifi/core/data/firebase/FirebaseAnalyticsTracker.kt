package com.salticusteam.vifi.core.data.firebase

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsUser
import com.salticusteam.vifi.core.model.firebaseName
import com.salticusteam.vifi.core.model.firebaseParameters
import javax.inject.Inject

/**
 * Reports anonymous usage events to Google Analytics for Firebase (iOS `FirebaseAnalyticsTracker` parity).
 *
 * Only screen names, archive levels and file formats are sent — never archive names or anything that could
 * identify the user.
 */
class FirebaseAnalyticsTracker @Inject constructor(
    private val analytics: FirebaseAnalytics,
) : AnalyticsTracker {

    override fun track(event: AnalyticsEvent) {
        analytics.logEvent(event.firebaseName, event.firebaseParameters?.toBundle())
    }

    override fun setUser(user: AnalyticsUser?) {
        analytics.setUserId(user?.id)
        // A name with no value (no profile, or sign-out) is cleared with null, so the next user is never mixed
        // with the previous one.
        val properties = user?.properties.orEmpty()
        for (name in AnalyticsUser.PROPERTY_NAMES) {
            analytics.setUserProperty(name, properties[name])
        }
    }

    private fun Map<String, Any>.toBundle(): Bundle = Bundle().also { bundle ->
        for ((key, value) in this) {
            when (value) {
                is String -> bundle.putString(key, value)
                is Long -> bundle.putLong(key, value)
                // Analytics has no boolean type; iOS sends NSNumber 1/0, which the SDK stores as a number.
                is Boolean -> bundle.putLong(key, if (value) 1L else 0L)
                else -> error("Unsupported analytics parameter type for $key")
            }
        }
    }
}
