package com.salticusteam.vifi.feature.login

import androidx.lifecycle.SavedStateHandle
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AuthError
import com.salticusteam.vifi.fake.RecordingLoginDiagnosticsReporter
import com.salticusteam.vifi.testing.MainDispatcherRule
import com.salticusteam.vifi.testing.MutableClock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LoginAnalyticsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var auth: FakeAuthService
    private lateinit var clock: MutableClock
    private lateinit var analytics: FakeAnalyticsTracker
    private lateinit var sut: LoginViewModel

    @Before
    fun setUp() {
        auth = FakeAuthService(isSignedIn = false, latency = kotlin.time.Duration.ZERO)
        clock = MutableClock()
        analytics = FakeAnalyticsTracker()
        sut = LoginViewModel(auth, RecordingLoginDiagnosticsReporter(), analytics, clock, SavedStateHandle())
    }

    private fun TestScope.sendCode() {
        sut.onPhoneTextChange("0532 123 45 67")
        sut.sendCode()
        advanceUntilIdle()
    }

    @Test
    fun `onScreenShown reports the Login screen view`() {
        sut.onScreenShown()

        assertEquals(listOf(AnalyticsEvent.ScreenView("Login")), analytics.events)
    }

    @Test
    fun `sendCode then correct code reports code requested and success`() = runTest {
        sendCode()
        sut.onCodeChange(FakeAuthService.VALID_CODE)
        advanceUntilIdle()

        assertEquals(
            listOf(AnalyticsEvent.LoginCodeRequested(isResend = false), AnalyticsEvent.LoginSucceeded),
            analytics.events,
        )
    }

    @Test
    fun `resendCode after the cooldown reports a resend`() = runTest {
        sendCode()
        clock.advance(LoginViewModel.RESEND_COOLDOWN)

        sut.resendCode()
        advanceUntilIdle()

        assertEquals(AnalyticsEvent.LoginCodeRequested(isResend = true), analytics.events.last())
    }

    @Test
    fun `sendCode within the cooldown for the same number reports nothing new`() = runTest {
        sendCode()
        sut.changeNumber()

        sut.sendCode()
        advanceUntilIdle()

        assertEquals(1, analytics.events.count { it is AnalyticsEvent.LoginCodeRequested })
    }

    @Test
    fun `wrong code reports login_failed at the verify step`() = runTest {
        sendCode()
        sut.onCodeChange("000000")
        advanceUntilIdle()

        assertEquals(AnalyticsEvent.LoginFailed(reason = "invalid_code", step = "verify"), analytics.events.last())
    }

    @Test
    fun `send failure reports login_failed at the send_code step`() = runTest {
        auth.sendFailure = AuthError.SmsTemporarilyUnavailable

        sendCode()

        assertEquals(
            listOf(
                AnalyticsEvent.LoginCodeRequested(isResend = false),
                AnalyticsEvent.LoginFailed(reason = "sms_temporarily_unavailable", step = "send_code"),
            ),
            analytics.events,
        )
    }

    @Test
    fun `instant verification reports success without a typed code`() = runTest {
        auth.verifiesInstantly = true

        sendCode()

        assertEquals(AnalyticsEvent.LoginSucceeded, analytics.events.last())
    }
}
