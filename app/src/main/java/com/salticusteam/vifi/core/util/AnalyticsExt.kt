package com.salticusteam.vifi.core.util

import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.model.AnalyticsEvent

/** Reports a manual screen view (Analytics' automatic screen reporting is disabled in the manifest). */
fun AnalyticsTracker.trackScreen(name: String) = track(AnalyticsEvent.ScreenView(name))
