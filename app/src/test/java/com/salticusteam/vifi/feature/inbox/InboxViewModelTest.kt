package com.salticusteam.vifi.feature.inbox

import com.salticusteam.vifi.core.data.announcements.AnnouncementsStore
import com.salticusteam.vifi.core.data.preferences.fake.FakeAnnouncementReadPreferences
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAnnouncementsReader
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationService
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.onEach
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val analytics = FakeAnalyticsTracker()

    @Test
    fun `opening reports the screen, marks all read, and keeps the new marks`() = runTest(UnconfinedTestDispatcher()) {
        val read = FakeAnnouncementReadPreferences(setOf("a1"))
        val (sut, store) = create(read = read)
        sut.uiState.onEach { }.launchIn(backgroundScope)

        sut.onScreenShown()
        advanceUntilIdle()

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.ScreenView(AnalyticsScreen.INBOX)), analytics.events)
        assertEquals(0, store.unreadCount.value)
        // a2 was unread on arrival and keeps its mark; a1 was read before.
        assertEquals(listOf("a2" to true, "a1" to false), sut.uiState.value.items.map { it.announcement.id to it.isUnread })
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

    private fun kotlinx.coroutines.test.TestScope.create(
        read: FakeAnnouncementReadPreferences = FakeAnnouncementReadPreferences(),
        permission: NotificationPermission = NotificationPermission.AUTHORIZED,
    ): Pair<InboxViewModel, AnnouncementsStore> {
        val store = AnnouncementsStore(
            FakeAnnouncementsReader(), read, SessionStore(FakeAuthService(isSignedIn = true), backgroundScope), backgroundScope,
        )
        return InboxViewModel(store, read, FakeNotificationService(permission), analytics) to store
    }
}
