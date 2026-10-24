package com.salticusteam.vifi.core.data.session

import com.salticusteam.vifi.core.data.preferences.fake.FakeRecentExamsRepository
import com.salticusteam.vifi.core.data.profile.ProfileStore
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import com.salticusteam.vifi.core.model.AnalyticsUser
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsIdentityTest {

    private val uid = FakeAuthService.DEFAULT_USER.id
    private val profile = FakeProfileService.SAMPLE_PROFILE
    private val profileStore = ProfileStore(FakeProfileService(mapOf(uid to profile)))
    private val analytics = FakeAnalyticsTracker()

    /** Starts the identity next to the real sign-out teardown, both following the same session. */
    private fun TestScope.startSut(auth: FakeAuthService): AnalyticsIdentity {
        val scope = backgroundScope
        val sessionStore = SessionStore(auth, scope)
        SessionTeardown(sessionStore, profileStore, FakeRecentExamsRepository(), analytics, scope).start()
        return AnalyticsIdentity(sessionStore, profileStore, auth, analytics, scope).apply { start() }
    }

    @Test
    fun `start when signed in identifies id and admin before the profile loads`() = runTest(UnconfinedTestDispatcher()) {
        startSut(FakeAuthService())

        assertEquals(listOf(AnalyticsUser.from(uid, profile = null, isAdmin = false)), analytics.users)
        assertEquals(mapOf("is_admin" to "false"), analytics.currentUser?.properties)
    }

    @Test
    fun `profile load enriches the identity with segment properties`() = runTest(UnconfinedTestDispatcher()) {
        startSut(FakeAuthService())

        profileStore.load(uid)

        assertEquals(AnalyticsUser.from(uid, profile, isAdmin = false), analytics.currentUser)
        assertEquals(
            mapOf(
                "is_admin" to "false",
                "university_id" to "bogazici-universitesi",
                "faculty_id" to "muhendislik-fakultesi",
                "department_id" to "bilgisayar-muhendisligi",
                "gender" to "female",
            ),
            analytics.currentUser?.properties,
        )
    }

    @Test
    fun `admin claim is reported as is_admin true`() = runTest(UnconfinedTestDispatcher()) {
        startSut(FakeAuthService(isAdmin = true))

        assertEquals("true", analytics.currentUser?.properties?.get("is_admin"))
    }

    @Test
    fun `profile edit re-identifies, an unchanged profile does not`() = runTest(UnconfinedTestDispatcher()) {
        startSut(FakeAuthService())
        profileStore.load(uid)
        val countAfterLoad = analytics.users.size

        profileStore.update(profile)
        assertEquals(countAfterLoad, analytics.users.size)

        profileStore.update(profile.copy(departmentId = "elektrik-muhendisligi"))
        assertEquals(countAfterLoad + 1, analytics.users.size)
        assertEquals("elektrik-muhendisligi", analytics.currentUser?.departmentId)
    }

    @Test
    fun `sign out clears the identity and nothing re-identifies afterwards`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthService()
        startSut(auth)
        profileStore.load(uid)

        auth.signOut()
        advanceUntilIdle()

        assertNull(analytics.users.last())
        assertNull(analytics.currentUser)
    }

    @Test
    fun `signing in after a sign-out identifies the new session`() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthService(isSignedIn = false, latency = Duration.ZERO)
        startSut(auth)
        assertTrue(analytics.users.none { it != null })

        val verificationId = auth.sendVerificationCode("+905321234567")
        auth.signIn(verificationId, FakeAuthService.VALID_CODE)
        advanceUntilIdle()

        assertEquals(uid, analytics.currentUser?.id)
    }

    @Test
    fun `start twice identifies once`() = runTest(UnconfinedTestDispatcher()) {
        startSut(FakeAuthService()).start()

        assertEquals(1, analytics.users.size)
    }
}
