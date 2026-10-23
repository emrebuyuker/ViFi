package com.salticusteam.vifi.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.preferences.OnboardingPreferences
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.AnalyticsValues
import com.salticusteam.vifi.core.model.OnboardingState
import com.salticusteam.vifi.core.util.trackScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The tour's behaviour (iOS OnboardingView + RootView): whether the first-launch tour is due, its analytics, and
 * marking it seen. Only the first-launch tour marks the per-device flag; a replay from About never does.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    // State
    val state: StateFlow<OnboardingState> = onboardingPreferences.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingState.Loading)

    // Intents
    fun onTourShown() {
        analytics.trackScreen(AnalyticsScreen.ONBOARDING)
    }

    /** The user finished ([method] `finished`) or skipped the tour that was opened from [source]. */
    fun onTourFinished(method: String, source: String) {
        analytics.track(AnalyticsEvent.OnboardingCompleted(method = method, source = source))
        if (source == AnalyticsValues.ONBOARDING_SOURCE_FIRST_LAUNCH) {
            viewModelScope.launch { onboardingPreferences.markSeen() }
        }
    }
}
