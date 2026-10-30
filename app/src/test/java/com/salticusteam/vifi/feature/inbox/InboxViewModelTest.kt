package com.salticusteam.vifi.feature.inbox

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.archive.ExamAccessResolver
import com.salticusteam.vifi.core.data.billing.SubscriptionStore
import com.salticusteam.vifi.core.data.coins.ExamUnlocksStore
import com.salticusteam.vifi.core.data.inbox.InboxStore
import com.salticusteam.vifi.core.data.preferences.fake.FakeLegacyInboxReadState
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeInboxReader
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationService
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
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.feature.browse.ExamUnlockEvent
import com.salticusteam.vifi.testing.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.ofEpochSecond(1_765_000_000)
    private val analytics = FakeAnalyticsTracker()
    // When the fake server stamps a marker write.
    private var serverNow = now
    private val reader = FakeInboxReader(now = { serverNow })

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
    fun `Premium, link and place actions lead there`() = runTest(UnconfinedTestDispatcher()) {
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
            sut.onAction(sut.uiState.value.entry("p-location"))
            assertEquals("ViFi Öğrenci Kafesi", (awaitItem() as InboxEvent.ChooseMapsApp).location.name)
            sut.onAction(sut.uiState.value.entry("a1"))
            expectNoEvents()
        }
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

    private fun InboxUiState.entry(id: String): InboxEntry = items.first { it.item.id == id }

    private fun personal(id: String, action: NotificationAction) = InboxItem(
        id, InboxSource.PERSONAL, NotificationKind.GENERAL, id, "", action = action, createdAt = now.minusSeconds(10),
    )

    private fun TestScope.create(
        permission: NotificationPermission = NotificationPermission.AUTHORIZED,
        subscribed: Boolean = false,
    ): Pair<InboxViewModel, InboxStore> {
        val scope = backgroundScope
        val store = InboxStore(reader, FakeLegacyInboxReadState(), Clock.fixed(now, ZoneOffset.UTC), scope)
        store.start(MutableStateFlow(SessionState.SignedIn(FakeAuthService.DEFAULT_USER)))
        val subscription = SubscriptionStore(FakeSubscriptionService(subscribed, latency = Duration.ZERO), scope)
        val unlocks = ExamUnlocksStore(FakeUnlocksReader(), SessionStore(FakeAuthService(), scope), scope)
        val gate = InboxExamGate(ExamAccessResolver(FakeArchiveRepository()), unlocks, subscription)
        return InboxViewModel(store, FakeNotificationService(permission), subscription, gate, analytics) to store
    }
}
