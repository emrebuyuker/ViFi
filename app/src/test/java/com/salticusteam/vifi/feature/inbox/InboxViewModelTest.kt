package com.salticusteam.vifi.feature.inbox

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.salticusteam.vifi.core.data.archive.ExamAccessResolver
import com.salticusteam.vifi.core.data.billing.SubscriptionStore
import com.salticusteam.vifi.core.data.coins.ExamUnlocksStore
import com.salticusteam.vifi.core.data.inbox.InboxStore
import com.salticusteam.vifi.core.data.notifications.NotificationEventTracker
import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsStore
import com.salticusteam.vifi.core.data.preferences.fake.FakeLegacyInboxReadState
import com.salticusteam.vifi.core.data.preferences.fake.FakeNotificationPromptHistory
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeInboxReader
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationEventReporter
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationService
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationSettingsService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubscriptionService
import com.salticusteam.vifi.core.data.repository.fake.FakeUnlocksReader
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.InboxItem
import com.salticusteam.vifi.core.model.InboxSource
import com.salticusteam.vifi.core.model.NotificationAction
import com.salticusteam.vifi.core.model.NotificationConsent
import com.salticusteam.vifi.core.model.NotificationEvent
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.model.NotificationPromptSource
import com.salticusteam.vifi.core.navigation.ModalPresence
import com.salticusteam.vifi.feature.browse.ExamUnlockEvent
import com.salticusteam.vifi.feature.notifications.NotificationPrompt
import com.salticusteam.vifi.feature.notifications.NotificationPromptCoordinator
import com.salticusteam.vifi.testing.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val events = FakeNotificationEventReporter()
    private var stores = 0
    private val now = Instant.ofEpochSecond(1_765_000_000)
    private val analytics = FakeAnalyticsTracker()
    // When the fake server stamps a marker write.
    private var serverNow = now
    private val reader = FakeInboxReader(now = { serverNow })
    // The device-wide prompt history, on the real clock (a re-ask is due 30 days after a "no").
    private val promptHistory = FakeNotificationPromptHistory()
    private lateinit var prompts: NotificationPromptCoordinator
    private val modals = ModalPresence()

    @Test
    fun `opening reports the screen and marks everything read in one write`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, store) = create()
        sut.uiState.onEach { }.launchIn(backgroundScope)
        assertTrue(store.unreadCount.value > 0)

        sut.onScreenShown()
        advanceUntilIdle()

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.ScreenView(AnalyticsScreen.INBOX)), analytics.events)
        assertEquals(1, reader.markReadCount)
        assertEquals(0, store.unreadCount.value)
        // The dots follow the marker live: none is left once it echoes back.
        assertTrue(sut.uiState.value.items.none { it.isUnread })
    }

    @Test
    fun `the inbox lists the merged unexpired notifications newest first`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, _) = create()
        sut.uiState.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(
            listOf("a1", "p-exam", "p-paywall", "p-exam-locked", "a2", "p-location"),
            sut.uiState.value.items.map { it.item.id },
        )
    }

    @Test
    fun `an arrival while the inbox is open is marked read again`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, store) = create()
        sut.uiState.onEach { }.launchIn(backgroundScope)
        sut.onScreenShown()
        advanceUntilIdle()

        serverNow = now.plusSeconds(20)
        reader.setBroadcast(
            FakeInboxReader.SAMPLE_BROADCAST +
                InboxItem("a-new", InboxSource.BROADCAST, NotificationKind.GENERAL, "Yeni", "", createdAt = now.plusSeconds(10)),
        )
        advanceUntilIdle()

        assertEquals(2, reader.markReadCount)
        assertEquals(0, store.unreadCount.value)
    }

    @Test
    fun `denied permission shows the settings banner`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, _) = create(permission = NotificationPermission.DENIED)
        sut.uiState.onEach { }.launchIn(backgroundScope)

        sut.onScreenShown()
        advanceUntilIdle()

        assertTrue(sut.uiState.value.showPermissionOffBanner)
    }

    @Test
    fun `allowed permission shows no banner`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, _) = create()
        sut.uiState.onEach { }.launchIn(backgroundScope)

        sut.onScreenShown()
        advanceUntilIdle()

        assertFalse(sut.uiState.value.showPermissionOffBanner)
    }

    // --- Cards and actions (INB-03) ---

    @Test
    fun `cards carry their action and image, and a subscriber's Premium card is inert`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, _) = create()
        sut.uiState.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(NotificationAction.Paywall, sut.uiState.value.entry("p-paywall").action)
        assertNull(sut.uiState.value.entry("a1").action)
        assertTrue(sut.uiState.value.entry("p-exam-locked").imageUrl.orEmpty().endsWith("notificationImages%2Fp-exam-locked.jpg?alt=media"))
        assertNull(sut.uiState.value.entry("a1").imageUrl)

        val (subscriber, _) = create(subscribed = true)
        subscriber.uiState.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        assertNull(subscriber.uiState.value.entry("p-paywall").action)
    }

    @Test
    fun `a push highlight shows only once its notification is listed`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, _) = create()
        sut.uiState.onEach { }.launchIn(backgroundScope)

        sut.onHighlightRequested("p-paywall")
        advanceUntilIdle()
        assertEquals("p-paywall", sut.uiState.value.highlightedId)

        sut.onHighlightRequested("a-expired")
        advanceUntilIdle()
        assertNull(sut.uiState.value.highlightedId)

        sut.onHighlightRequested(null)
        advanceUntilIdle()
        assertNull(sut.uiState.value.highlightedId)
    }

    @Test
    fun `Premium, add-exam and link actions lead there`() = runTest(UnconfinedTestDispatcher()) {
        reader.setPersonal(
            listOf(
                personal("p-add", NotificationAction.AddExam),
                personal("p-url", NotificationAction.Url("https://vifi.app/kampanya")),
            ) + FakeInboxReader.SAMPLE_PERSONAL,
        )
        val (sut, _) = create()
        sut.uiState.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        sut.events.test {
            sut.onAction(sut.uiState.value.entry("p-paywall"))
            assertEquals(InboxEvent.OpenPaywall, awaitItem())
            sut.onAction(sut.uiState.value.entry("p-add"))
            assertEquals(InboxEvent.OpenAddExam(lesson = null), awaitItem())
            sut.onAction(sut.uiState.value.entry("p-url"))
            assertEquals(InboxEvent.OpenUrl("https://vifi.app/kampanya"), awaitItem())
            sut.onAction(sut.uiState.value.entry("a1"))
            expectNoEvents()
        }
    }

    @Test
    fun `a place action offers the maps app choice until one is picked or declined`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, _) = create()
        sut.uiState.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        sut.events.test {
            sut.onAction(sut.uiState.value.entry("p-location"))
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals("ViFi Öğrenci Kafesi", sut.uiState.value.locationChoice?.name)

        sut.onLocationChoiceDismissed()
        advanceUntilIdle()
        assertNull(sut.uiState.value.locationChoice)
    }

    @Test
    fun `a free exam opens, a locked one offers the unlock sheet and opens once unlocked`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, _) = create()
        sut.uiState.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        sut.events.test {
            sut.onAction(sut.uiState.value.entry("p-exam"))
            val free = awaitItem() as InboxEvent.OpenExam
            assertEquals("2020-final", free.path.components.last())
            assertEquals(ExamKind.PDF, free.kind)

            sut.onAction(sut.uiState.value.entry("p-exam-locked"))
            advanceUntilIdle()
            expectNoEvents()
            val locked = checkNotNull(sut.uiState.value.lockedExam)
            assertEquals("2022-final", locked.path.components.last())

            sut.onUnlockEvent(ExamUnlockEvent.Unlocked(locked))
            assertEquals(InboxEvent.OpenExam(locked.path, ExamKind.IMAGES), awaitItem())
            advanceUntilIdle()
            assertNull(sut.uiState.value.lockedExam)
        }
    }

    @Test
    fun `earning coins from the unlock sheet opens the add-exam form for the lesson`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, _) = create()
        sut.uiState.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        sut.onAction(sut.uiState.value.entry("p-exam-locked"))
        advanceUntilIdle()
        val lesson = checkNotNull(sut.uiState.value.lockedExam).path.parent

        sut.events.test {
            sut.onUnlockEvent(ExamUnlockEvent.EarnCoins(lesson))
            assertEquals(InboxEvent.OpenAddExam(lesson), awaitItem())
        }
    }

    // --- Statistics (INB-05) ---

    @Test
    fun `opened is reported once the tapped push's notification is listed, never for the bell`() =
        runTest(UnconfinedTestDispatcher()) {
            val (sut, _) = create()
            sut.uiState.onEach { }.launchIn(backgroundScope)
            advanceUntilIdle()

            sut.onHighlightRequested(null)
            sut.onHighlightRequested("a-expired")
            advanceUntilIdle()
            assertTrue(events.reports.isEmpty())

            sut.onHighlightRequested("p-paywall")
            advanceUntilIdle()
            assertEquals(listOf(NotificationEvent.OPENED to "p-paywall"), events.reports)
        }

    @Test
    fun `using an action is reported, a locked exam included, and an inert card is not`() =
        runTest(UnconfinedTestDispatcher()) {
            val (sut, _) = create()
            sut.uiState.onEach { }.launchIn(backgroundScope)
            advanceUntilIdle()

            sut.onAction(sut.uiState.value.entry("p-exam-locked"))
            sut.onAction(sut.uiState.value.entry("a1"))
            advanceUntilIdle()

            assertEquals(listOf(NotificationEvent.ACTION_TAPPED to "p-exam-locked"), events.reports)
        }

    // --- Consent re-ask (NIT-01, iOS ConsentReaskPresenter) ---

    @Test
    fun `the consent is asked again 700 ms after the page shows, once due`() = runTest(UnconfinedTestDispatcher()) {
        promptHistory.consentDeclinedAt = Instant.now().minus(31, ChronoUnit.DAYS)
        val (sut, _) = create(consent = NotificationConsent(marketing = false))
        sut.uiState.onEach { }.launchIn(backgroundScope)

        sut.onScreenResumed()
        advanceTimeBy(InboxViewModel.CONSENT_REASK_DELAY_MS - 1)
        assertNull(prompts.pending.value)

        advanceUntilIdle()
        assertEquals(NotificationPrompt.Kind.CONSENT, prompts.pending.value?.kind)
        assertEquals(NotificationPromptSource.REASK, prompts.pending.value?.source)
    }

    @Test
    fun `someone reading a tapped push is not asked`() = runTest(UnconfinedTestDispatcher()) {
        promptHistory.consentDeclinedAt = Instant.now().minus(31, ChronoUnit.DAYS)
        val (sut, _) = create(consent = NotificationConsent(marketing = false))
        sut.uiState.onEach { }.launchIn(backgroundScope)

        sut.onHighlightRequested("p-paywall")
        sut.onScreenResumed()
        advanceUntilIdle()

        assertTrue(prompts.isFree)
    }

    @Test
    fun `leaving the page before its moment drops the re-ask, the next showing offers it`() =
        runTest(UnconfinedTestDispatcher()) {
            promptHistory.consentDeclinedAt = Instant.now().minus(31, ChronoUnit.DAYS)
            val (sut, _) = create(consent = NotificationConsent(marketing = false))
            sut.uiState.onEach { }.launchIn(backgroundScope)

            sut.onScreenResumed()
            advanceTimeBy(InboxViewModel.CONSENT_REASK_DELAY_MS / 2)
            sut.onScreenPaused()
            advanceUntilIdle()
            assertTrue(prompts.isFree)

            sut.onScreenResumed()
            advanceUntilIdle()
            assertEquals(NotificationPrompt.Kind.CONSENT, prompts.pending.value?.kind)
        }

    @Test
    fun `a sheet over the page holds the re-ask until the page has been clear for 700 ms`() =
        runTest(UnconfinedTestDispatcher()) {
            promptHistory.consentDeclinedAt = Instant.now().minus(31, ChronoUnit.DAYS)
            val (sut, _) = create(consent = NotificationConsent(marketing = false))
            sut.uiState.onEach { }.launchIn(backgroundScope)

            sut.onScreenResumed()
            advanceTimeBy(InboxViewModel.CONSENT_REASK_DELAY_MS / 2)
            // The unlock sheet opens before the moment comes, and stays a while.
            modals.opened()
            advanceTimeBy(InboxViewModel.CONSENT_REASK_DELAY_MS * 3)
            assertTrue(prompts.isFree)

            // Closed: the wait starts over, so leaving right after it (the exam it unlocked) asks nothing.
            modals.closed()
            advanceTimeBy(InboxViewModel.CONSENT_REASK_DELAY_MS - 1)
            assertTrue(prompts.isFree)

            advanceUntilIdle()
            assertEquals(NotificationPrompt.Kind.CONSENT, prompts.pending.value?.kind)
        }

    @Test
    fun `nothing is asked again before 30 days or with campaigns on`() = runTest(UnconfinedTestDispatcher()) {
        promptHistory.consentDeclinedAt = Instant.now().minus(10, ChronoUnit.DAYS)
        val (tooSoon, _) = create(consent = NotificationConsent(marketing = false))
        tooSoon.onScreenResumed()
        advanceUntilIdle()
        assertTrue(prompts.isFree)

        promptHistory.consentDeclinedAt = Instant.now().minus(31, ChronoUnit.DAYS)
        val (campaignsOn, _) = create(consent = NotificationConsent(marketing = true))
        campaignsOn.onScreenResumed()
        advanceUntilIdle()
        assertTrue(prompts.isFree)
    }

    private fun InboxUiState.entry(id: String): InboxEntry = items.first { it.item.id == id }

    private fun personal(id: String, action: NotificationAction) = InboxItem(
        id, InboxSource.PERSONAL, NotificationKind.GENERAL, id, "", action = action, createdAt = now.minusSeconds(10),
    )

    private fun TestScope.create(
        permission: NotificationPermission = NotificationPermission.AUTHORIZED,
        subscribed: Boolean = false,
        consent: NotificationConsent? = null,
    ): Pair<InboxViewModel, InboxStore> {
        val scope = backgroundScope
        val store = InboxStore(reader, FakeLegacyInboxReadState(), Clock.fixed(now, ZoneOffset.UTC), scope)
        store.start(MutableStateFlow(SessionState.SignedIn(FakeAuthService.DEFAULT_USER)))
        val subscription = SubscriptionStore(FakeSubscriptionService(subscribed, latency = Duration.ZERO), scope)
        val unlocks = ExamUnlocksStore(FakeUnlocksReader(), SessionStore(FakeAuthService(), scope), scope)
        val gate = InboxExamGate(ExamAccessResolver(FakeArchiveRepository()), unlocks, subscription)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            // DataStore only takes a `.preferences_pb` file.
            produceFile = { tmp.newFile("events-${stores++}.preferences_pb").also { it.delete() } },
        )
        val tracker = NotificationEventTracker(events, dataStore, scope)
        val session = SessionStore(FakeAuthService(), scope)
        val notifications = FakeNotificationService(permission)
        val settings = NotificationSettingsStore(FakeNotificationSettingsService(consent), scope)
            .apply { start(MutableStateFlow(SessionState.SignedIn(FakeAuthService.DEFAULT_USER))) }
        prompts = NotificationPromptCoordinator(notifications, settings, promptHistory)
        val sut = InboxViewModel(store, notifications, subscription, gate, session, tracker, analytics, prompts, modals)
        return sut to store
    }
}
