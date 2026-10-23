package com.salticusteam.vifi.core.data.session

import com.salticusteam.vifi.core.data.preferences.fake.FakeRecentExamsRepository
import com.salticusteam.vifi.core.data.profile.ProfileState
import com.salticusteam.vifi.core.data.profile.ProfileStore
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionTeardownTest {

    private val uid = FakeAuthService.DEFAULT_USER.id
    private val auth = FakeAuthService()
    private val profileStore = ProfileStore(FakeProfileService(mapOf(uid to FakeProfileService.SAMPLE_PROFILE)))
    private val recents = FakeRecentExamsRepository()
    private val analytics = FakeAnalyticsTracker()

    private fun TestScope.startSut(): SessionTeardown {
        val scope = backgroundScope
        val sessionStore = SessionStore(auth, scope)
        return SessionTeardown(sessionStore, profileStore, recents, analytics, scope).apply { start() }
    }

    @Test
    fun `sign out resets the profile, clears recents and the analytics identity`() = runTest(UnconfinedTestDispatcher()) {
        profileStore.load(uid)
        recents.record(ArchivePath(listOf("U", "F", "D", "L", "A")), ExamKind.PDF, at = 1)
        startSut()
        assertTrue(analytics.users.isEmpty())

        auth.signOut()
        advanceUntilIdle()

        assertEquals(ProfileState.Loading, profileStore.state.value)
        assertTrue(recents.exams.first().isEmpty())
        assertEquals(listOf(null), analytics.users)
    }

    @Test
    fun `start twice tears down once per sign-out`() = runTest(UnconfinedTestDispatcher()) {
        val sut = startSut()
        sut.start()

        auth.signOut()
        advanceUntilIdle()

        assertEquals(1, analytics.users.size)
    }
}
