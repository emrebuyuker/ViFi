package com.salticusteam.vifi.core.data.preferences

import com.salticusteam.vifi.core.model.OnboardingState
import kotlinx.coroutines.flow.Flow

/**
 * Device-wide "tour seen" flag (iOS OnboardingStore). Deliberately independent of the signed-in user:
 * sign-out and profile changes never reset it.
 */
interface OnboardingPreferences {

    /** [OnboardingState.Loading] until the flag is read; [OnboardingState.Presenting] on a fresh install. */
    val state: Flow<OnboardingState>

    suspend fun markSeen()

    /** Debug/test helper (iOS `OnboardingStore.seed`). */
    suspend fun seed(seen: Boolean)
}
