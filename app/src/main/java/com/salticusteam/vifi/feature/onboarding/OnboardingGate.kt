package com.salticusteam.vifi.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.core.model.AnalyticsValues
import com.salticusteam.vifi.core.model.OnboardingState

/**
 * Presents the tour once per device over [content] (iOS RootView `fullScreenCover`). The archive keeps loading
 * beneath; nothing shows while the flag is still being read, so there is no flash. Finishing or skipping marks
 * the flag, which hides the tour here.
 */
@Composable
fun OnboardingGate(
    viewModel: OnboardingViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    content()

    if (state == OnboardingState.Presenting) {
        OnboardingScreen(
            source = AnalyticsValues.ONBOARDING_SOURCE_FIRST_LAUNCH,
            onFinish = {},
            viewModel = viewModel,
        )
    }
}
