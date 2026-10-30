package com.salticusteam.vifi.app

import androidx.lifecycle.ViewModel
import com.salticusteam.vifi.core.data.preferences.OnboardingPreferences
import com.salticusteam.vifi.core.model.OnboardingState
import com.salticusteam.vifi.core.notifications.NotificationRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Hands the tapped pushes to [AppRoot]'s archive host: each one once, after the first-launch tour (iOS waits for the
 * tour to finish) and once no modal is in the way ([NotificationRouter.deliveries]).
 */
@HiltViewModel
class DeepLinkViewModel @Inject constructor(
    notificationRouter: NotificationRouter,
    onboardingPreferences: OnboardingPreferences,
) : ViewModel() {

    // A tour still being read may yet show, so only a known "hidden" lets a tap through.
    val inboxOpenings: Flow<NotificationRouter.Request> =
        notificationRouter.deliveries(ready = onboardingPreferences.state.map { it == OnboardingState.Hidden })
}
