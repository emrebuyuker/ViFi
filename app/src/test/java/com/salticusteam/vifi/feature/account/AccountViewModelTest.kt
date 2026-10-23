package com.salticusteam.vifi.feature.account

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.profile.ProfileStore
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val uid = FakeAuthService.DEFAULT_USER.id
    private val authService = FakeAuthService(isSignedIn = true)
    private val analytics = FakeAnalyticsTracker()

    @Test
    fun `uiState shows the profile name and the formatted phone number`() = runTest {
        val profileStore = ProfileStore(FakeProfileService(mapOf(uid to FakeProfileService.SAMPLE_PROFILE)))
        profileStore.load(uid)
        val sut = AccountViewModel(SessionStore(authService, backgroundScope), profileStore, authService, analytics)

        assertEquals(
            AccountUiState(profileName = "Ayşe Yılmaz", formattedPhoneNumber = "+90 532 123 45 67"),
            sut.uiState.value,
        )
    }

    @Test
    fun `uiState follows a profile edit`() = runTest {
        val profileStore = ProfileStore(FakeProfileService(mapOf(uid to FakeProfileService.SAMPLE_PROFILE)))
        profileStore.load(uid)
        val sut = AccountViewModel(SessionStore(authService, backgroundScope), profileStore, authService, analytics)

        sut.uiState.test {
            assertEquals("Ayşe Yılmaz", awaitItem().profileName)
            profileStore.update(FakeProfileService.SAMPLE_PROFILE.copy(name = "Zeynep"))
            assertEquals("Zeynep Yılmaz", awaitItem().profileName)
        }
    }

    @Test
    fun `uiState without a loaded profile has no name`() = runTest {
        val sut = AccountViewModel(
            SessionStore(authService, backgroundScope),
            ProfileStore(FakeProfileService()),
            authService,
            analytics,
        )

        assertNull(sut.uiState.value.profileName)
        assertEquals("+90 532 123 45 67", sut.uiState.value.formattedPhoneNumber)
    }

    @Test
    fun `signOut signs the user out and reports it`() = runTest {
        val sut = AccountViewModel(
            SessionStore(authService, backgroundScope),
            ProfileStore(FakeProfileService()),
            authService,
            analytics,
        )

        sut.signOut()

        assertNull(authService.currentUser)
        assertEquals(listOf(AnalyticsEvent.SignedOut), analytics.events)
    }

    @Test
    fun `onScreenShown reports the account screen`() = runTest {
        val sut = AccountViewModel(
            SessionStore(authService, backgroundScope),
            ProfileStore(FakeProfileService()),
            authService,
            analytics,
        )

        sut.onScreenShown()

        assertEquals(listOf(AnalyticsEvent.ScreenView(AnalyticsScreen.ACCOUNT)), analytics.events)
    }
}
