package com.salticusteam.vifi.feature.notifications

import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsStore
import com.salticusteam.vifi.core.data.preferences.fake.FakeNotificationPromptHistory
import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.data.repository.NotificationSettingsService
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationService
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationSettingsService
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.NotificationConsent
import com.salticusteam.vifi.core.model.NotificationConsentSource
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.model.NotificationPromptSource
import com.salticusteam.vifi.feature.notifications.NotificationPermissionFlow.Page
import com.salticusteam.vifi.feature.notifications.NotificationPrompt.Kind
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * iOS `NotificationPermissionFlowModelTests` (15) plus Android's own: the system dialog that can still be shown after a
 * "no", a flow closed while that dialog is up, and the answer being saved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPermissionFlowTest {

    private var now = Instant.parse("2026-10-30T09:00:00Z")
    private val history = FakeNotificationPromptHistory(now = { now })
    private val service = FakeNotificationSettingsService()
    private val analytics = FakeAnalyticsTracker()
    private val uid = FakeAuthService.DEFAULT_USER.id

    private fun later(days: Long) {
        now = now.plus(Duration.ofDays(days))
    }

    private fun TestScope.store(
        consent: NotificationConsent? = null,
        settingsService: NotificationSettingsService = service,
    ): NotificationSettingsStore {
        service.set(consent)
        return NotificationSettingsStore(settingsService, backgroundScope)
            .apply { start(MutableStateFlow(SessionState.SignedIn(FakeAuthService.DEFAULT_USER))) }
    }

    private suspend fun TestScope.flow(
        kind: Kind,
        source: NotificationPromptSource,
        notifications: NotificationService = FakeNotificationService(NotificationPermission.NOT_DETERMINED),
        settings: NotificationSettingsStore = store(),
    ): NotificationPermissionFlow = NotificationPermissionFlow.open(
        prompt = NotificationPrompt(kind, source, notifications.permission()),
        notifications = notifications,
        settings = settings,
        history = history,
        analytics = analytics,
    )

    // --- Showing ---

    @Test
    fun `the first page is recorded and reported when it appears, once`() = runTest(UnconfinedTestDispatcher()) {
        val flow = flow(Kind.PERMISSION, NotificationPromptSource.PROMPT)

        flow.start()
        flow.start()

        assertTrue(history.primingOffered)
        assertEquals(listOf(AnalyticsEvent.NotificationPrimingShown(NotificationPromptSource.PROMPT)), analytics.events)
    }

    @Test
    fun `a stand-alone consent offer starts on the consent and records it as offered`() =
        runTest(UnconfinedTestDispatcher()) {
            val flow = flow(Kind.CONSENT, NotificationPromptSource.PROMPT)

            flow.start()

            assertEquals(Page.CONSENT, flow.state.value.page)
            assertTrue(history.consentOffered)
            assertFalse(history.primingOffered)
            assertEquals(listOf(AnalyticsEvent.MarketingConsentShown(NotificationPromptSource.PROMPT)), analytics.events)
        }

    // --- Step 1 of 2 ---

    @Test
    fun `enabling asks the system, then moves on to the consent`() = runTest(UnconfinedTestDispatcher()) {
        val notifications = FakeNotificationService(NotificationPermission.NOT_DETERMINED)
        val flow = flow(Kind.PERMISSION, NotificationPromptSource.PROMPT, notifications)
        flow.start()

        flow.enableNotifications()

        assertEquals(1, notifications.requestCount)
        assertEquals(NotificationPermission.AUTHORIZED, flow.state.value.permission)
        assertEquals(Page.CONSENT, flow.state.value.page)
        assertFalse(flow.state.value.isFinished)
        assertFalse(flow.state.value.isWorking)
        assertTrue(history.consentOffered)
        assertEquals(
            listOf(
                AnalyticsEvent.NotificationPrimingShown(NotificationPromptSource.PROMPT),
                AnalyticsEvent.NotificationPrimingAccepted(NotificationPromptSource.PROMPT),
                AnalyticsEvent.MarketingConsentShown(NotificationPromptSource.PROMPT),
            ),
            analytics.events,
        )
    }

    @Test
    fun `a system refusal ends the flow and counts as a no for the re-ask`() = runTest(UnconfinedTestDispatcher()) {
        val notifications = FakeNotificationService(NotificationPermission.NOT_DETERMINED, grantOnRequest = false)
        val flow = flow(Kind.PERMISSION, NotificationPromptSource.PROMPT, notifications)
        flow.start()

        flow.enableNotifications()

        assertTrue(flow.state.value.isFinished)
        assertEquals(Page.PERMISSION, flow.state.value.page)
        assertFalse("No consent is asked of someone who refused notifications", history.consentOffered)
        later(31)
        assertTrue(history.shouldReaskPermission())
    }

    @Test
    fun `someone who already answered the consent elsewhere is not asked again`() = runTest(UnconfinedTestDispatcher()) {
        val flow = flow(Kind.PERMISSION, NotificationPromptSource.PROMPT, settings = store(NotificationConsent(false)))
        flow.start()

        flow.enableNotifications()

        assertTrue(flow.state.value.isFinished)
        assertFalse(history.consentOffered)
        assertTrue(service.writes.isEmpty())
    }

    @Test
    fun `not now ends the flow and starts the wait for the one re-ask`() = runTest(UnconfinedTestDispatcher()) {
        val notifications = FakeNotificationService(NotificationPermission.NOT_DETERMINED)
        val flow = flow(Kind.PERMISSION, NotificationPromptSource.PROMPT, notifications)
        flow.start()

        flow.declineNotifications()

        assertTrue(flow.state.value.isFinished)
        assertEquals(AnalyticsEvent.NotificationPrimingDeclined(NotificationPromptSource.PROMPT), analytics.events.last())
        assertEquals("The system dialog is kept for a user who says yes", 0, notifications.requestCount)
        later(30)
        assertTrue(history.shouldReaskPermission())
    }

    @Test
    fun `when the system will not ask again, the flow offers the settings and opening them counts as a yes`() =
        runTest(UnconfinedTestDispatcher()) {
            val notifications = FakeNotificationService(NotificationPermission.DENIED)
            history.recordPermissionDeclined()
            later(31)
            val flow = flow(Kind.PERMISSION, NotificationPromptSource.REASK, notifications)
            flow.start()

            assertTrue(flow.state.value.needsSettingsApp)
            flow.settingsWereOpened()
            flow.sheetDidClose()

            assertTrue(flow.state.value.isFinished)
            assertEquals(
                listOf(
                    AnalyticsEvent.NotificationPrimingShown(NotificationPromptSource.REASK),
                    AnalyticsEvent.NotificationPrimingAccepted(NotificationPromptSource.REASK),
                ),
                analytics.events,
            )
            assertEquals("The system cannot be asked again", 0, notifications.requestCount)
            assertFalse(history.shouldReaskPermission())
        }

    @Test
    fun `a no the system still asks again for keeps the system dialog`() = runTest(UnconfinedTestDispatcher()) {
        val notifications = FakeNotificationService(NotificationPermission.DENIED, canRequest = true)

        val flow = flow(Kind.PERMISSION, NotificationPromptSource.REASK, notifications)

        assertFalse(flow.state.value.needsSettingsApp)
    }

    @Test
    fun `a re-ask is recorded as shown, so it is offered only once`() = runTest(UnconfinedTestDispatcher()) {
        history.recordPermissionDeclined()
        later(31)
        assertTrue(history.shouldReaskPermission())

        flow(Kind.PERMISSION, NotificationPromptSource.REASK).start()

        assertFalse(history.shouldReaskPermission())
    }

    @Test
    fun `a flow closed while the system dialog is up does not move on to the consent`() =
        runTest(UnconfinedTestDispatcher()) {
            val notifications = GatedNotificationService()
            val flow = flow(Kind.PERMISSION, NotificationPromptSource.PROMPT, notifications)
            flow.start()
            val enabling = launch { flow.enableNotifications() }
            assertTrue(flow.state.value.isWorking)

            flow.sheetDidClose()
            notifications.answer.complete(true)
            enabling.join()

            assertEquals(Page.PERMISSION, flow.state.value.page)
            assertFalse(flow.state.value.isWorking)
            assertFalse(history.consentOffered)
            assertEquals(NotificationPermission.AUTHORIZED, flow.state.value.permission)
        }

    // --- Step 2 of 2 ---

    @Test
    fun `yes writes the consent as a prompt answer and ends the flow`() = runTest(UnconfinedTestDispatcher()) {
        val settings = store()
        val flow = flow(Kind.CONSENT, NotificationPromptSource.PROMPT, settings = settings)
        flow.start()

        flow.answerConsent(true)

        assertEquals(
            listOf(FakeNotificationSettingsService.Write(uid, true, NotificationConsentSource.PROMPT)),
            service.writes,
        )
        assertTrue(settings.state.value.marketingEnabled)
        assertTrue(flow.state.value.isFinished)
        assertEquals(AnalyticsEvent.MarketingConsentAccepted(NotificationPromptSource.PROMPT), analytics.events.last())
        later(400)
        assertFalse("A yes needs no re-ask", history.shouldReaskConsent())
    }

    @Test
    fun `no writes the consent too, and starts the wait for the one re-ask`() = runTest(UnconfinedTestDispatcher()) {
        val settings = store()
        val flow = flow(Kind.CONSENT, NotificationPromptSource.PROMPT, settings = settings)
        flow.start()

        flow.answerConsent(false)

        assertEquals(
            listOf(FakeNotificationSettingsService.Write(uid, false, NotificationConsentSource.PROMPT)),
            service.writes,
        )
        assertFalse(settings.state.value.marketingEnabled)
        assertTrue(settings.state.value.hasAnswered)
        assertTrue(flow.state.value.isFinished)
        assertEquals(AnalyticsEvent.MarketingConsentDeclined(NotificationPromptSource.PROMPT), analytics.events.last())
        later(30)
        assertTrue(history.shouldReaskConsent())
    }

    @Test
    fun `a consent that cannot be saved keeps the page open with an error, and reports nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val settings = store()
            service.writeFailure = IOException("PERMISSION_DENIED")
            val flow = flow(Kind.CONSENT, NotificationPromptSource.PROMPT, settings = settings)
            flow.start()

            flow.answerConsent(true)

            assertTrue(flow.state.value.saveFailed)
            assertTrue(flow.state.value.canDismiss)
            assertFalse(flow.state.value.isFinished)
            assertFalse(settings.state.value.marketingEnabled)
            assertEquals(listOf(AnalyticsEvent.MarketingConsentShown(NotificationPromptSource.PROMPT)), analytics.events)

            service.writeFailure = null
            flow.answerConsent(true)
            assertFalse(flow.state.value.saveFailed)
            assertTrue(flow.state.value.isFinished)
            assertTrue(settings.state.value.marketingEnabled)
        }

    @Test
    fun `the answer being saved is the one shown as working`() = runTest(UnconfinedTestDispatcher()) {
        val unacknowledged = object : NotificationSettingsService {
            override fun consentChanges(uid: String): Flow<NotificationConsent?> = flowOf(null)
            override suspend fun setMarketing(uid: String, enabled: Boolean, source: NotificationConsentSource) =
                awaitCancellation()
        }
        val flow = flow(Kind.CONSENT, NotificationPromptSource.PROMPT, settings = store(settingsService = unacknowledged))
        flow.start()

        val answering = launch { flow.answerConsent(false) }
        assertEquals(false, flow.state.value.answering)
        assertTrue(flow.state.value.isWorking)
        assertFalse(flow.state.value.canDismiss)

        advanceTimeBy(NotificationSettingsStore.SAVE_TIMEOUT_MS + 1)
        answering.join()
        assertNull(flow.state.value.answering)
        assertTrue("A write not acknowledged in time counts as saved", flow.state.value.isFinished)
    }

    @Test
    fun `the consent after a re-asked permission is the consent's first offer, not its re-ask`() =
        runTest(UnconfinedTestDispatcher()) {
            history.recordConsentDeclined()
            later(31)
            val flow = flow(Kind.PERMISSION, NotificationPromptSource.REASK)
            flow.start()

            flow.enableNotifications()

            assertEquals(Page.CONSENT, flow.state.value.page)
            assertTrue(AnalyticsEvent.MarketingConsentShown(NotificationPromptSource.REASK) in analytics.events)
            assertTrue("The consent's own re-ask is still unused", history.shouldReaskConsent())
        }

    // --- Closing ---

    @Test
    fun `closing the permission page unanswered counts as a no for the schedule, and writes no consent`() =
        runTest(UnconfinedTestDispatcher()) {
            val flow = flow(Kind.PERMISSION, NotificationPromptSource.PROMPT)
            flow.start()

            flow.sheetDidClose()

            later(30)
            assertTrue(history.shouldReaskPermission())
            assertTrue(service.writes.isEmpty())
        }

    @Test
    fun `closing the consent page unanswered never opts the user in`() = runTest(UnconfinedTestDispatcher()) {
        val settings = store()
        val flow = flow(Kind.CONSENT, NotificationPromptSource.PROMPT, settings = settings)
        flow.start()

        flow.sheetDidClose()

        later(30)
        assertTrue(history.shouldReaskConsent())
        assertTrue(service.writes.isEmpty())
        assertFalse(settings.state.value.marketingEnabled)
    }

    @Test
    fun `closing after an answer changes nothing`() = runTest(UnconfinedTestDispatcher()) {
        val flow = flow(Kind.CONSENT, NotificationPromptSource.PROMPT)
        flow.start()
        flow.answerConsent(true)

        flow.sheetDidClose()

        later(400)
        assertFalse(history.shouldReaskConsent())
    }

    /** The system dialog stays up until the test answers it. */
    private class GatedNotificationService : NotificationService {
        val answer = CompletableDeferred<Boolean>()
        private var current = NotificationPermission.NOT_DETERMINED

        override suspend fun permission(): NotificationPermission = current

        override suspend fun requestAuthorization(): Boolean = answer.await().also { granted ->
            if (granted) current = NotificationPermission.AUTHORIZED
        }

        override suspend fun canRequestPermission(): Boolean = current != NotificationPermission.AUTHORIZED
        override suspend fun token(): String? = null
        override suspend fun subscribe(topic: String) = Unit
        override suspend fun unsubscribe(topic: String) = Unit
    }
}
