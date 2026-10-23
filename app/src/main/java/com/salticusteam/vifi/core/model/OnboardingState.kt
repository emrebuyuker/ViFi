package com.salticusteam.vifi.core.model

// Added in 2.5 — whether the first-launch tour should show; [Loading] avoids a flash while the flag is read asynchronously.
sealed interface OnboardingState {
    data object Loading : OnboardingState
    data object Presenting : OnboardingState
    data object Hidden : OnboardingState
}
