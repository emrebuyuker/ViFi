package com.salticusteam.vifi.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.ui.graphics.vector.ImageVector
import com.salticusteam.vifi.R

/** Which colour a tour page's hero badge takes (iOS `.accentColor` / `.orange`). */
enum class OnboardingTint { ACCENT, ORANGE }

/** A single page of the tour (iOS OnboardingPage). */
data class OnboardingPage(
    val id: Int,
    val icon: ImageVector,
    val tint: OnboardingTint,
    @StringRes val title: Int,
    @StringRes val message: Int,
) {
    companion object {
        /** The four tour pages, in order. */
        val tour: List<OnboardingPage> = listOf(
            OnboardingPage(0, Icons.Filled.School, OnboardingTint.ACCENT, R.string.onboarding_p1_title, R.string.onboarding_p1_message),
            OnboardingPage(1, Icons.Filled.AutoStories, OnboardingTint.ACCENT, R.string.onboarding_p2_title, R.string.onboarding_p2_message),
            OnboardingPage(2, Icons.Filled.Lock, OnboardingTint.ORANGE, R.string.onboarding_p3_title, R.string.onboarding_p3_message),
            OnboardingPage(3, Icons.Filled.AddCircle, OnboardingTint.ORANGE, R.string.onboarding_p4_title, R.string.onboarding_p4_message),
        )
    }
}
