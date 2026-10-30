package com.salticusteam.vifi.feature.notifications

import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsStore
import com.salticusteam.vifi.core.data.preferences.fake.FakeNotificationPromptHistory
import com.salticusteam.vifi.core.data.preferences.fake.FakeOnboardingPreferences
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationService
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationSettingsService
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.model.NotificationPromptSource
import com.salticusteam.vifi.core.navigation.ModalPresence
import com.salticusteam.vifi.core.notifications.NotificationRouter
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The presenter: when a queued run goes on screen, and every way it leaves (iOS `NotificationPromptPresenter`). */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPromptViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val history = FakeNotificationPromptHistory()
    private val analytics = FakeAnalyticsTracker()
    private val modals = ModalPresence()
    private val auth = FakeAuthService()

    private class Graph(
        val sut: NotificationPromptViewModel,
        val coordinator: NotificationPromptCoordinator,
        val router: NotificationRouter,
    )

    private fun TestScope.graph(
        permission: NotificationPermission = NotificationPermission.NOT_DETERMINED,
        onboarding: FakeOnboardingPreferences = FakeOnboardingPreferences(seen = true),
    ): Graph {
        val notifications = FakeNotificationService(permission)
        val sessions = SessionStore(auth, backgroundScope)
        val settings = NotificationSettingsStore(FakeNotificationSettingsService(), backgroundScope)
            .apply { start(sessions.state) }
        val coordinator = NotificationPromptCoordinator(notifications, settings, history)
        val router = NotificationRouter(modals, backgroundScope)
        val sut = NotificationPromptViewModel(
            coordinator, notifications, settings, history, analytics, modals, router, onboarding, sessions,
        )
        return Graph(sut, coordinator, router)
    }

    @Test
    fun `a waiting run goes on screen once nothing is in the way`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, coordinator) = graph().let { it.sut to it.coordinator }

        coordinator.offerFirstRunIfNeeded()
        advanceUntilIdle()

        val shown = sut.flow.value
        assertNotNull(shown)
        assertEquals(coordinator.presented.value, shown?.prompt)
        assertEquals(NotificationPrompt.Kind.PERMISSION, shown?.prompt?.kind)
        assertNull(coordinator.pending.value)
    }

    @Test
    fun `a run is recorded only once its sheet is on screen`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, coordinator) = graph().let { it.sut to it.coordinator }
        coordinator.offerFirstRunIfNeeded()
        advanceUntilIdle()
        assertFalse(history.primingOffered)

        sut.onShown()
        advanceUntilIdle()

        assertTrue(history.primingOffered)
        assertEquals(listOf(AnalyticsEvent.NotificationPrimingShown(NotificationPromptSource.PROMPT)), analytics.events)
    }

    @Test
    fun `an open modal holds the run until it closes`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, coordinator) = graph().let { it.sut to it.coordinator }
        modals.opened()

        coordinator.offerFirstRunIfNeeded()
        advanceUntilIdle()
        assertNull(sut.flow.value)
        assertNotNull(coordinator.pending.value)

        modals.closed()
        advanceUntilIdle()
        assertNotNull(sut.flow.value)
    }

    @Test
    fun `the first offer waits for the tour`() = runTest(UnconfinedTestDispatcher()) {
        val onboarding = FakeOnboardingPreferences(seen = false)
        val (sut, coordinator) = graph(onboarding = onboarding).let { it.sut to it.coordinator }

        sut.onArchiveShown()
        advanceUntilIdle()
        assertNull(coordinator.pending.value)
        assertNull(sut.flow.value)

        onboarding.markSeen()
        advanceUntilIdle()
        assertNotNull(sut.flow.value)
    }

    @Test
    fun `a tapped push takes the screen and the unanswered page counts as a no`() = runTest(UnconfinedTestDispatcher()) {
        val graph = graph()
        graph.coordinator.offerFirstRunIfNeeded()
        advanceUntilIdle()
        graph.sut.onShown()
        advanceUntilIdle()

        graph.router.receive("n-1")
        advanceUntilIdle()

        assertNull(graph.sut.flow.value)
        assertTrue(graph.coordinator.isFree)
        assertNotNull(history.permissionDeclinedAt)
    }

    @Test
    fun `a run queued while a tap is on its way to the inbox waits for it`() = runTest(UnconfinedTestDispatcher()) {
        val graph = graph()
        graph.router.receive("n-1")
        advanceUntilIdle()

        graph.coordinator.offerFirstRunIfNeeded()
        advanceUntilIdle()
        assertNull(graph.sut.flow.value)

        graph.router.reset()
        advanceUntilIdle()
        assertNotNull(graph.sut.flow.value)
    }

    @Test
    fun `signing out ends the run`() = runTest(UnconfinedTestDispatcher()) {
        val graph = graph()
        graph.coordinator.offerFirstRunIfNeeded()
        advanceUntilIdle()
        graph.sut.onShown()
        advanceUntilIdle()

        auth.signOut()
        advanceUntilIdle()

        assertNull(graph.sut.flow.value)
        assertTrue(graph.coordinator.isFree)
        assertNotNull(history.permissionDeclinedAt)
    }

    @Test
    fun `swiping the sheet away frees the coordinator and records the no`() = runTest(UnconfinedTestDispatcher()) {
        val graph = graph()
        graph.coordinator.offerFirstRunIfNeeded()
        advanceUntilIdle()
        graph.sut.onShown()
        advanceUntilIdle()

        graph.sut.onSheetClosed()
        advanceUntilIdle()

        assertNull(graph.sut.flow.value)
        assertTrue(graph.coordinator.isFree)
        assertNotNull(history.permissionDeclinedAt)
    }

    @Test
    fun `both steps run through the sheet, then it closes with nothing more recorded`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = graph()
            graph.coordinator.offerFirstRunIfNeeded()
            advanceUntilIdle()
            val flow = graph.sut.flow.value
            graph.sut.onShown()
            graph.sut.onEnable()
            advanceUntilIdle()
            assertEquals(NotificationPermissionFlow.Page.CONSENT, flow?.state?.value?.page)

            graph.sut.onAnswer(true)
            advanceUntilIdle()
            assertEquals(true, flow?.state?.value?.isFinished)
            graph.sut.onSheetClosed()
            advanceUntilIdle()

            assertNull(graph.sut.flow.value)
            assertTrue(graph.coordinator.isFree)
            assertNull(history.permissionDeclinedAt)
            assertNull(history.consentDeclinedAt)
            assertEquals(AnalyticsEvent.MarketingConsentAccepted(NotificationPromptSource.PROMPT), analytics.events.last())
        }

    @Test
    fun `not now through the sheet records the no once`() = runTest(UnconfinedTestDispatcher()) {
        val graph = graph()
        graph.coordinator.offerFirstRunIfNeeded()
        advanceUntilIdle()
        graph.sut.onShown()

        graph.sut.onNotNow()
        advanceUntilIdle()

        assertEquals(true, graph.sut.flow.value?.state?.value?.isFinished)
        assertEquals(AnalyticsEvent.NotificationPrimingDeclined(NotificationPromptSource.PROMPT), analytics.events.last())
        assertNotNull(history.permissionDeclinedAt)
    }

    @Test
    fun `nothing is shown while signed out`() = runTest(UnconfinedTestDispatcher()) {
        auth.signOut()
        val (sut, coordinator) = graph().let { it.sut to it.coordinator }

        coordinator.offerFirstRunIfNeeded()
        advanceUntilIdle()

        assertNull(sut.flow.value)
    }
}
