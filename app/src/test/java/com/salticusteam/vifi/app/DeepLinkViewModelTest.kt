package com.salticusteam.vifi.app

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.preferences.OnboardingPreferences
import com.salticusteam.vifi.core.model.OnboardingState
import com.salticusteam.vifi.core.navigation.ModalPresence
import com.salticusteam.vifi.core.notifications.NotificationRouter
import com.salticusteam.vifi.core.notifications.NotificationRouter.Request
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** A tapped push reaches the inbox only once the first-launch tour is known to be over (appendix A.6). */
@OptIn(ExperimentalCoroutinesApi::class)
class DeepLinkViewModelTest {

    private val tour = MutableStateFlow<OnboardingState>(OnboardingState.Loading)

    private fun TestScope.sut(): Pair<DeepLinkViewModel, NotificationRouter> {
        val router = NotificationRouter(ModalPresence(), backgroundScope)
        return DeepLinkViewModel(router, TourPreferences(tour)) to router
    }

    @Test
    fun `a tap waits while the tour is unknown or showing, then opens the inbox once it is over`() =
        runTest(UnconfinedTestDispatcher()) {
            val (sut, router) = sut()
            router.receive("n-1")

            sut.inboxOpenings.test {
                expectNoEvents()
                tour.value = OnboardingState.Presenting
                expectNoEvents()

                tour.value = OnboardingState.Hidden
                assertEquals(Request("n-1"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `with the tour already seen a tap opens the inbox at once, and only once`() = runTest(UnconfinedTestDispatcher()) {
        tour.value = OnboardingState.Hidden
        val (sut, router) = sut()

        sut.inboxOpenings.test {
            router.receive("n-1")
            assertEquals(Request("n-1"), awaitItem())
            expectNoEvents()
        }
    }

    /** The tour flag as a test sets it, including the brief [OnboardingState.Loading] the fake never shows. */
    private class TourPreferences(override val state: MutableStateFlow<OnboardingState>) : OnboardingPreferences {
        override suspend fun markSeen() { state.value = OnboardingState.Hidden }
        override suspend fun seed(seen: Boolean) {
            state.value = if (seen) OnboardingState.Hidden else OnboardingState.Presenting
        }
    }
}
