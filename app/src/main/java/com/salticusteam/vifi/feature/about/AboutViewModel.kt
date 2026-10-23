package com.salticusteam.vifi.feature.about

import androidx.lifecycle.ViewModel
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.util.trackScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** The About screen's only behaviour is its screen view (iOS AboutView reports none; added for parity with Account). */
@HiltViewModel
class AboutViewModel @Inject constructor(
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    // Intents
    fun onScreenShown() {
        analytics.trackScreen(AnalyticsScreen.ABOUT)
    }
}
