package com.salticusteam.vifi.feature.inbox

import com.salticusteam.vifi.core.data.inbox.InboxStore
import com.salticusteam.vifi.core.data.preferences.fake.FakeLegacyInboxReadState
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeInboxReader
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationService
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.InboxItem
import com.salticusteam.vifi.core.model.InboxSource
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.testing.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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

    private fun TestScope.create(
        permission: NotificationPermission = NotificationPermission.AUTHORIZED,
    ): Pair<InboxViewModel, InboxStore> {
        val store = InboxStore(reader, FakeLegacyInboxReadState(), Clock.fixed(now, ZoneOffset.UTC), backgroundScope)
        store.start(MutableStateFlow(SessionState.SignedIn(FakeAuthService.DEFAULT_USER)))
        return InboxViewModel(store, FakeNotificationService(permission), analytics) to store
    }
}
