package com.salticusteam.vifi.feature.account

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.salticusteam.vifi.core.data.billing.SubscriptionStore
import com.salticusteam.vifi.core.data.coins.CoinWalletStore
import com.salticusteam.vifi.core.data.local.fake.FakeLocalDataCleaner
import com.salticusteam.vifi.core.data.profile.ProfileStore
import com.salticusteam.vifi.core.data.repository.DeviceRegistration
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeDeviceRegistration
import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubscriptionService
import com.salticusteam.vifi.core.data.repository.fake.FakeWalletReader
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.AuthError
import com.salticusteam.vifi.core.model.ProfileError
import com.salticusteam.vifi.feature.login.LoginViewModel
import com.salticusteam.vifi.testing.MainDispatcherRule
import com.salticusteam.vifi.testing.MutableClock
import com.salticusteam.vifi.testing.afterProcessDeath
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration as KotlinDuration

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val uid = FakeAuthService.DEFAULT_USER.id
    private val analytics = FakeAnalyticsTracker()
    private val cleaner = FakeLocalDataCleaner()
    private val clock = MutableClock()
    private val registration = FakeDeviceRegistration()

    private fun TestScope.makeSut(
        authService: FakeAuthService = FakeAuthService(isSignedIn = true, latency = KotlinDuration.ZERO),
        profileService: FakeProfileService = FakeProfileService(mapOf(uid to FakeProfileService.SAMPLE_PROFILE)),
        profileStore: ProfileStore = ProfileStore(profileService),
        walletReader: FakeWalletReader = FakeWalletReader(),
        sessionStore: SessionStore = SessionStore(authService, backgroundScope),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        subscriptionService: FakeSubscriptionService = FakeSubscriptionService(latency = KotlinDuration.ZERO),
        deviceRegistration: DeviceRegistration = registration,
    ) = AccountViewModel(
        sessionStore = sessionStore,
        profileStore = profileStore,
        coinWalletStore = CoinWalletStore(walletReader, sessionStore, backgroundScope),
        subscriptionStore = SubscriptionStore(subscriptionService, backgroundScope),
        authService = authService,
        profileService = profileService,
        deviceRegistration = deviceRegistration,
        localDataCleaner = cleaner,
        analytics = analytics,
        clock = clock,
        // The test scope itself, so the post-deletion purge is foreground work advanceUntilIdle waits for.
        // The confirmed sign-out too.
        appScope = this,
        savedStateHandle = savedStateHandle,
    )

    private fun reauthAuthService() =
        FakeAuthService(isSignedIn = true, latency = KotlinDuration.ZERO, requiresRecentLoginForDeletion = true)

    // --- Profile, coins and phone ---

    @Test
    fun `uiState shows the live coin balance of the signed-in user`() = runTest {
        val sut = makeSut(walletReader = FakeWalletReader(balance = 1250))

        // The wallet listener runs in backgroundScope, which advanceUntilIdle alone does not drain.
        advanceUntilIdle()
        runCurrent()

        assertEquals(1250, sut.uiState.value.coinBalance)
    }

    @Test
    fun `uiState follows the Premium state, independent of the signed-in user`() = runTest {
        val subscription = FakeSubscriptionService(latency = KotlinDuration.ZERO)
        val sut = makeSut(subscriptionService = subscription)
        advanceUntilIdle()
        runCurrent()
        assertFalse(sut.uiState.value.isSubscribed)

        subscription.setSubscribed(true)
        runCurrent()
        assertTrue(sut.uiState.value.isSubscribed)

        subscription.setSubscribed(false)
        runCurrent()
        assertFalse(sut.uiState.value.isSubscribed)
    }

    @Test
    fun `uiState starts subscribed when Premium is already active`() = runTest {
        val sut = makeSut(subscriptionService = FakeSubscriptionService(subscribed = true, latency = KotlinDuration.ZERO))
        advanceUntilIdle()
        runCurrent()

        assertTrue(sut.uiState.value.isSubscribed)
    }

    @Test
    fun `onScreenShown reveals moderation to an admin only`() = runTest {
        val adminAuth = FakeAuthService(isSignedIn = true, latency = KotlinDuration.ZERO, isAdmin = true)
        val admin = makeSut(authService = adminAuth)
        val user = makeSut()

        admin.onScreenShown()
        user.onScreenShown()
        advanceUntilIdle()

        assertTrue(admin.uiState.value.isAdmin)
        assertFalse(user.uiState.value.isAdmin)
    }

    @Test
    fun `uiState shows the profile name and the formatted phone number`() = runTest {
        val profileStore = ProfileStore(FakeProfileService(mapOf(uid to FakeProfileService.SAMPLE_PROFILE)))
        profileStore.load(uid)
        val sut = makeSut(profileStore = profileStore)

        assertEquals(
            AccountUiState(profileName = "Ayşe Yılmaz", formattedPhoneNumber = "+90 532 123 45 67"),
            sut.uiState.value,
        )
    }

    @Test
    fun `uiState follows a profile edit`() = runTest {
        val profileStore = ProfileStore(FakeProfileService(mapOf(uid to FakeProfileService.SAMPLE_PROFILE)))
        profileStore.load(uid)
        val sut = makeSut(profileStore = profileStore)

        sut.uiState.test {
            assertEquals("Ayşe Yılmaz", awaitItem().profileName)
            profileStore.update(FakeProfileService.SAMPLE_PROFILE.copy(name = "Zeynep"))
            assertEquals("Zeynep Yılmaz", awaitItem().profileName)
        }
    }

    @Test
    fun `uiState without a loaded profile has no name`() = runTest {
        val sut = makeSut(profileService = FakeProfileService())

        assertNull(sut.uiState.value.profileName)
        assertEquals("+90 532 123 45 67", sut.uiState.value.formattedPhoneNumber)
    }

    @Test
    fun `signOut signs the user out and reports it`() = runTest {
        val authService = FakeAuthService(isSignedIn = true)
        val sut = makeSut(authService = authService)

        sut.signOut()
        advanceUntilIdle()

        assertNull(authService.currentUser)
        assertEquals(listOf(uid), registration.unregistered)
        assertEquals(listOf(AnalyticsEvent.SignedOut), analytics.events)
        assertFalse(sut.uiState.value.isSigningOut)
    }

    // --- Sign-out cleanup (iOS AccountSignOutCleanupTests) ---

    @Test
    fun `signOut removes the push registration first, while still signed in`() = runTest {
        val authService = FakeAuthService(isSignedIn = true, latency = KotlinDuration.ZERO)
        val order = mutableListOf<String>()
        val hook = object : DeviceRegistration {
            override suspend fun unregister(uid: String) {
                order += "hook $uid, signed in: ${authService.currentUser != null}"
            }
        }
        val sut = makeSut(authService = authService, deviceRegistration = hook)

        sut.signOut()
        advanceUntilIdle()

        assertEquals(listOf("hook $uid, signed in: true"), order)
        assertNull(authService.currentUser)
    }

    @Test
    fun `a second signOut while the cleanup runs is ignored`() = runTest {
        val authService = FakeAuthService(isSignedIn = true, latency = KotlinDuration.ZERO)
        val gate = CompletableDeferred<Unit>()
        val hook = GatedRegistration(gate)
        val sut = makeSut(authService = authService, deviceRegistration = hook)

        sut.signOut()
        runCurrent()
        assertTrue(sut.uiState.value.isSigningOut)
        assertNotNull("The session lasts until the cleanup is done", authService.currentUser)

        sut.signOut()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(uid), hook.unregistered)
        assertNull(authService.currentUser)
        assertEquals(listOf(AnalyticsEvent.SignedOut), analytics.events)
        assertFalse(sut.uiState.value.isSigningOut)
    }

    @Test
    fun `deleteAccount while signing out does nothing`() = runTest {
        val profileService = FakeProfileService(mapOf(uid to FakeProfileService.SAMPLE_PROFILE))
        val gate = CompletableDeferred<Unit>()
        val sut = makeSut(profileService = profileService, deviceRegistration = GatedRegistration(gate))
        sut.signOut()
        runCurrent()

        sut.deleteAccount()
        runCurrent()

        assertFalse(sut.uiState.value.isWorking)
        assertTrue(profileService.calls.isEmpty())
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `onScreenShown reports the account screen`() = runTest {
        val sut = makeSut()

        sut.onScreenShown()

        assertEquals(listOf(AnalyticsEvent.ScreenView(AnalyticsScreen.ACCOUNT)), analytics.events)
    }

    // --- deleteAccount ---

    @Test
    fun `deleteAccount when no recent login is needed deletes the profile then the account and purges`() = runTest {
        val authService = FakeAuthService(isSignedIn = true, latency = KotlinDuration.ZERO)
        val profileService = FakeProfileService(mapOf(uid to FakeProfileService.SAMPLE_PROFILE))
        val sut = makeSut(authService = authService, profileService = profileService)

        sut.deleteAccount()
        advanceUntilIdle()

        assertNull(profileService.storedProfile(uid))
        assertEquals(listOf(FakeProfileService.Call.Delete(uid)), profileService.calls)
        assertNull(authService.currentUser)
        assertEquals(listOf(AnalyticsEvent.AccountDeleted), analytics.events)
        assertEquals(1, cleaner.purgeCount)
        assertTrue(authService.sentCodeNumbers.isEmpty())
    }

    @Test
    fun `deleteAccount removes the push registration before the profile, while still signed in`() = runTest {
        val authService = FakeAuthService(isSignedIn = true, latency = KotlinDuration.ZERO)
        val profileService = FakeProfileService(mapOf(uid to FakeProfileService.SAMPLE_PROFILE))
        val order = mutableListOf<String>()
        val hook = object : DeviceRegistration {
            override suspend fun unregister(uid: String) {
                order += "hook $uid, signed in: ${authService.currentUser != null}, " +
                    "profile calls: ${profileService.calls.size}"
            }
        }
        val sut = makeSut(authService = authService, profileService = profileService, deviceRegistration = hook)

        sut.deleteAccount()
        advanceUntilIdle()

        assertEquals(listOf("hook $uid, signed in: true, profile calls: 0"), order)
        assertEquals(listOf(FakeProfileService.Call.Delete(uid)), profileService.calls)
        assertNull(authService.currentUser)
    }

    @Test
    fun `deleteAccount removes the profile while the user is still signed in`() = runTest {
        val authService = FakeAuthService(isSignedIn = true, latency = KotlinDuration.ZERO)
        val profileService = FakeProfileService(mapOf(uid to FakeProfileService.SAMPLE_PROFILE))
        val gate = CompletableDeferred<Unit>()
        profileService.gate = gate
        val sut = makeSut(authService = authService, profileService = profileService)

        sut.deleteAccount()
        runCurrent()

        assertTrue(sut.uiState.value.isWorking)
        assertEquals(listOf(FakeProfileService.Call.Delete(uid)), profileService.calls)
        assertNotNull(authService.currentUser)

        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(authService.currentUser)
    }

    @Test
    fun `deleteAccount when the profile cannot be deleted keeps the account and shows the error`() = runTest {
        val authService = FakeAuthService(isSignedIn = true, latency = KotlinDuration.ZERO)
        val profileService = FakeProfileService(mapOf(uid to FakeProfileService.SAMPLE_PROFILE))
        profileService.failure = ProfileError.Offline
        val sut = makeSut(authService = authService, profileService = profileService)

        sut.deleteAccount()
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isWorking)
        assertEquals(ProfileError.Offline.userMessage, state.error)
        assertNotNull(authService.currentUser)
        assertEquals(0, cleaner.purgeCount)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `deleteAccount when signed out reports the ended session`() = runTest {
        val sut = makeSut(authService = FakeAuthService(isSignedIn = false, latency = KotlinDuration.ZERO))

        sut.deleteAccount()
        advanceUntilIdle()

        assertEquals(AuthError.NotSignedIn.userMessage, sut.uiState.value.error)
        assertFalse(sut.uiState.value.isWorking)
    }

    @Test
    fun `deleteAccount when a recent login is required sends a code to the same number`() = runTest {
        val authService = reauthAuthService()
        val profileService = FakeProfileService(mapOf(uid to FakeProfileService.SAMPLE_PROFILE))
        val sut = makeSut(authService = authService, profileService = profileService)

        sut.deleteAccount()
        advanceUntilIdle()

        val state = sut.uiState.value
        assertTrue(state.isReverifying)
        assertFalse(state.isWorking)
        assertEquals("", state.codeText)
        assertNull(state.error)
        assertEquals(clock.now.plus(LoginViewModel.RESEND_COOLDOWN), state.resendAvailableAt)
        assertEquals(listOf(FakeAuthService.DEFAULT_USER.phoneNumber), authService.sentCodeNumbers)
        // The profile is removed before the recent-login prompt, as on iOS.
        assertNull(profileService.storedProfile(uid))
        assertNotNull(authService.currentUser)
        assertEquals(0, cleaner.purgeCount)
    }

    @Test
    fun `deleteAccount when the number verifies instantly deletes without a code`() = runTest {
        val authService = reauthAuthService().apply { verifiesInstantly = true }
        val sut = makeSut(authService = authService)

        sut.deleteAccount()
        advanceUntilIdle()

        assertFalse(sut.uiState.value.isReverifying)
        assertNull(authService.currentUser)
        assertEquals(listOf(AnalyticsEvent.AccountDeleted), analytics.events)
        assertEquals(1, cleaner.purgeCount)
    }

    @Test
    fun `deleteAccount when the code cannot be sent shows the error`() = runTest {
        val authService = reauthAuthService().apply { sendFailure = AuthError.TooManyRequests }
        val sut = makeSut(authService = authService)

        sut.deleteAccount()
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isReverifying)
        assertFalse(state.isWorking)
        assertEquals(AuthError.TooManyRequests.userMessage, state.error)
    }

    // --- Re-verification ---

    @Test
    fun `onCodeChange keeps digits only and confirms on the sixth digit`() = runTest {
        val authService = reauthAuthService()
        val sut = makeSut(authService = authService)
        sut.deleteAccount()
        advanceUntilIdle()

        sut.onCodeChange("12a3")
        runCurrent()
        assertEquals("123", sut.uiState.value.codeText)
        assertFalse(sut.uiState.value.canConfirmReverification)

        sut.onCodeChange(FakeAuthService.VALID_CODE + "9")
        advanceUntilIdle()

        assertNull(authService.currentUser)
        assertEquals(listOf(AnalyticsEvent.AccountDeleted), analytics.events)
        assertEquals(1, cleaner.purgeCount)
    }

    @Test
    fun `confirmReverification with a wrong code clears the field and keeps the cooldown`() = runTest {
        val authService = reauthAuthService()
        val sut = makeSut(authService = authService)
        sut.deleteAccount()
        advanceUntilIdle()
        val cooldown = sut.uiState.value.resendAvailableAt

        sut.onCodeChange("000000")
        advanceUntilIdle()

        val state = sut.uiState.value
        assertEquals("", state.codeText)
        assertEquals(AuthError.InvalidCode.userMessage, state.error)
        assertEquals(cooldown, state.resendAvailableAt)
        assertTrue(state.isReverifying)
        assertFalse(state.isWorking)
        assertNotNull(authService.currentUser)
    }

    @Test
    fun `confirmReverification with an expired code clears the field and the cooldown`() = runTest {
        val authService = reauthAuthService()
        val sut = makeSut(authService = authService)
        sut.deleteAccount()
        advanceUntilIdle()
        authService.signInFailure = AuthError.CodeExpired

        sut.onCodeChange(FakeAuthService.VALID_CODE)
        advanceUntilIdle()

        val state = sut.uiState.value
        assertEquals("", state.codeText)
        assertEquals(AuthError.CodeExpired.userMessage, state.error)
        assertNull(state.resendAvailableAt)
        assertTrue(sut.canResendCode())
    }

    @Test
    fun `typing after a failure clears the error`() = runTest {
        val sut = makeSut(authService = reauthAuthService())
        sut.deleteAccount()
        advanceUntilIdle()
        sut.onCodeChange("000000")
        advanceUntilIdle()

        sut.onCodeChange("1")
        runCurrent()

        assertNull(sut.uiState.value.error)
    }

    @Test
    fun `resendCode is allowed only after the cooldown`() = runTest {
        val authService = reauthAuthService()
        val sut = makeSut(authService = authService)
        sut.deleteAccount()
        advanceUntilIdle()

        assertEquals(60, sut.resendCooldownRemaining())
        sut.resendCode()
        advanceUntilIdle()
        assertEquals(1, authService.sentCodeNumbers.size)

        clock.advance(Duration.ofSeconds(59))
        assertEquals(1, sut.resendCooldownRemaining())
        clock.advance(Duration.ofSeconds(1))
        assertTrue(sut.canResendCode())

        sut.resendCode()
        advanceUntilIdle()

        assertEquals(2, authService.sentCodeNumbers.size)
        assertEquals(clock.now.plus(LoginViewModel.RESEND_COOLDOWN), sut.uiState.value.resendAvailableAt)
    }

    @Test
    fun `cancelReverification resets the deletion state`() = runTest {
        val authService = reauthAuthService()
        val sut = makeSut(authService = authService)
        sut.deleteAccount()
        advanceUntilIdle()
        sut.onCodeChange("12")

        sut.cancelReverification()
        runCurrent()

        val state = sut.uiState.value
        assertFalse(state.isReverifying)
        assertEquals("", state.codeText)
        assertNull(state.resendAvailableAt)
        assertNull(state.error)
        assertNotNull(authService.currentUser)
        assertEquals("+90 532 123 45 67", state.formattedPhoneNumber)
    }

    @Test
    fun `signOut while a deletion runs does nothing`() = runTest {
        val authService = FakeAuthService(isSignedIn = true, latency = KotlinDuration.ZERO)
        val profileService = FakeProfileService(mapOf(uid to FakeProfileService.SAMPLE_PROFILE))
        profileService.gate = CompletableDeferred()
        val sut = makeSut(authService = authService, profileService = profileService)
        sut.deleteAccount()
        runCurrent()

        sut.signOut()
        runCurrent()

        assertNotNull(authService.currentUser)
        assertFalse(sut.uiState.value.isSigningOut)
        // Only the deletion's own removal, no sign-out's.
        assertEquals(listOf(uid), registration.unregistered)
        assertTrue(analytics.events.isEmpty())
    }

    // --- Process death ---

    @Test
    fun `process death during re-verification restores the code entry and still deletes`() = runTest {
        val authService = reauthAuthService()
        val handle = SavedStateHandle()
        val first = makeSut(authService = authService, savedStateHandle = handle)
        first.deleteAccount()
        advanceUntilIdle()
        first.onCodeChange("12")
        advanceUntilIdle()
        clock.advance(Duration.ofSeconds(20))

        val restored = makeSut(authService = authService, savedStateHandle = handle.afterProcessDeath())
        runCurrent()

        val state = restored.uiState.value
        assertTrue(state.isReverifying)
        assertFalse(state.isWorking)
        assertEquals("12", state.codeText)
        assertEquals(40, restored.resendCooldownRemaining())

        restored.onCodeChange(FakeAuthService.VALID_CODE)
        advanceUntilIdle()
        assertNull(authService.currentUser)
        assertEquals(1, authService.sentCodeNumbers.size)
        assertEquals(1, cleaner.purgeCount)
    }

    @Test
    fun `process death after cancelling re-verification starts without the code entry`() = runTest {
        val authService = reauthAuthService()
        val handle = SavedStateHandle()
        val first = makeSut(authService = authService, savedStateHandle = handle)
        first.deleteAccount()
        advanceUntilIdle()
        first.cancelReverification()
        advanceUntilIdle()

        val restored = makeSut(authService = authService, savedStateHandle = handle.afterProcessDeath())
        runCurrent()

        assertFalse(restored.uiState.value.isReverifying)
        assertNull(restored.uiState.value.resendAvailableAt)
    }

    /** Holds each removal until [gate] opens (the 3 s cleanup still running). */
    private class GatedRegistration(private val gate: CompletableDeferred<Unit>) : DeviceRegistration {

        /** The uids removed, in order of the calls. */
        val unregistered = mutableListOf<String>()

        override suspend fun unregister(uid: String) {
            unregistered += uid
            gate.await()
        }
    }
}
