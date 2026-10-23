package com.salticusteam.vifi.core.data

import com.salticusteam.vifi.core.data.announcements.AnnouncementsStore
import com.salticusteam.vifi.core.data.preferences.fake.FakeAnnouncementReadPreferences
import com.salticusteam.vifi.core.data.repository.fake.FakeAnnouncementsReader
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementsStoreTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sample = FakeAnnouncementsReader.SAMPLE

    @Test
    fun `signed in the list loads and everything is unread`() = runTest(UnconfinedTestDispatcher()) {
        val sut = AnnouncementsStore(FakeAnnouncementsReader(), FakeAnnouncementReadPreferences(), session(true), backgroundScope)
        advanceUntilIdle()

        assertEquals(sample, sut.state.value.announcements)
        assertFalse(sut.state.value.isLoading)
        assertEquals(2, sut.unreadCount.value)
    }

    @Test
    fun `markAllRead clears the unread count`() = runTest(UnconfinedTestDispatcher()) {
        val sut = AnnouncementsStore(FakeAnnouncementsReader(), FakeAnnouncementReadPreferences(), session(true), backgroundScope)
        advanceUntilIdle()

        sut.markAllRead()
        advanceUntilIdle()

        assertEquals(0, sut.unreadCount.value)
    }

    @Test
    fun `an announcement published later counts as unread`() = runTest(UnconfinedTestDispatcher()) {
        val reader = FakeAnnouncementsReader()
        val sut = AnnouncementsStore(reader, FakeAnnouncementReadPreferences(setOf("a1", "a2")), session(true), backgroundScope)
        advanceUntilIdle()
        assertEquals(0, sut.unreadCount.value)

        reader.set(sample + sample.first().copy(id = "a3"))
        advanceUntilIdle()

        assertEquals(1, sut.unreadCount.value)
    }

    @Test
    fun `signed out nothing is read`() = runTest(UnconfinedTestDispatcher()) {
        val sut = AnnouncementsStore(FakeAnnouncementsReader(), FakeAnnouncementReadPreferences(), session(false), backgroundScope)
        advanceUntilIdle()
        assertTrue(sut.state.value.announcements.isEmpty())
        assertFalse(sut.state.value.isLoading)
        assertEquals(0, sut.unreadCount.value)
    }

    @Test
    fun `a failed read surfaces the Turkish message`() = runTest(UnconfinedTestDispatcher()) {
        val sut = AnnouncementsStore(
            FakeAnnouncementsReader(failure = ArchiveError.Offline), FakeAnnouncementReadPreferences(), session(true), backgroundScope,
        )
        advanceUntilIdle()

        assertEquals(ArchiveError.Offline.userMessage, sut.state.value.error)
        assertFalse(sut.state.value.isLoading)
    }

    private fun kotlinx.coroutines.test.TestScope.session(signedIn: Boolean) =
        SessionStore(FakeAuthService(isSignedIn = signedIn), backgroundScope)
}
