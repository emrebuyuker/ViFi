package com.salticusteam.vifi.feature.onboarding

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.preferences.fake.FakeOnboardingPreferences
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.AnalyticsValues
import com.salticusteam.vifi.core.model.OnboardingState
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val analytics = FakeAnalyticsTracker()

    private fun viewModel(preferences: FakeOnboardingPreferences) = OnboardingViewModel(preferences, analytics)

    @Test
    fun `state on a fresh install presents the tour`() = runTest {
        val sut = viewModel(FakeOnboardingPreferences(seen = false))

        sut.state.test {
            // Loading is the initial value until the flag is read.
            assertEquals(OnboardingState.Loading, awaitItem())
            assertEquals(OnboardingState.Presenting, awaitItem())
        }
    }

    @Test
    fun `state after the tour was seen stays hidden`() = runTest {
        val sut = viewModel(FakeOnboardingPreferences(seen = true))

        sut.state.test {
            assertEquals(OnboardingState.Loading, awaitItem())
            assertEquals(OnboardingState.Hidden, awaitItem())
        }
    }

    @Test
    fun `onTourShown reports the Onboarding screen view`() {
        viewModel(FakeOnboardingPreferences()).onTourShown()

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.ScreenView(AnalyticsScreen.ONBOARDING)), analytics.events)
    }

    @Test
    fun `finishing the first-launch tour reports completion and marks it seen`() = runTest {
        val preferences = FakeOnboardingPreferences(seen = false)
        val sut = viewModel(preferences)

        sut.onTourFinished(AnalyticsValues.ONBOARDING_FINISHED, AnalyticsValues.ONBOARDING_SOURCE_FIRST_LAUNCH)
        advanceUntilIdle()

        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.OnboardingCompleted("finished", "first_launch")),
            analytics.events,
        )
        assertEquals(OnboardingState.Hidden, preferences.state.first())
    }

    @Test
    fun `skipping the first-launch tour also marks it seen`() = runTest {
        val preferences = FakeOnboardingPreferences(seen = false)
        val sut = viewModel(preferences)

        sut.onTourFinished(AnalyticsValues.ONBOARDING_SKIPPED, AnalyticsValues.ONBOARDING_SOURCE_FIRST_LAUNCH)
        advanceUntilIdle()

        assertEquals(AnalyticsEvent.OnboardingCompleted("skipped", "first_launch"), analytics.events.single())
        assertEquals(OnboardingState.Hidden, preferences.state.first())
    }

    @Test
    fun `a replay reports completion with the replay source and never touches the flag`() = runTest {
        val preferences = FakeOnboardingPreferences(seen = false)
        val sut = viewModel(preferences)

        sut.onTourFinished(AnalyticsValues.ONBOARDING_FINISHED, AnalyticsValues.ONBOARDING_SOURCE_REPLAY)
        advanceUntilIdle()

        assertEquals(AnalyticsEvent.OnboardingCompleted("finished", "replay"), analytics.events.single())
        assertEquals(OnboardingState.Presenting, preferences.state.first())
    }
}
