package com.salticusteam.vifi.feature.login

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.model.AuthError
import com.salticusteam.vifi.core.model.LoginFailureStage
import com.salticusteam.vifi.core.model.PhoneNumber
import com.salticusteam.vifi.fake.RecordingLoginDiagnosticsReporter
import com.salticusteam.vifi.testing.MainDispatcherRule
import com.salticusteam.vifi.testing.MutableClock
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var auth: FakeAuthService
    private lateinit var diagnostics: RecordingLoginDiagnosticsReporter
    private lateinit var clock: MutableClock
    private lateinit var sut: LoginViewModel

    private val number = PhoneNumber.fromNational("5321234567")

    @Before
    fun setUp() {
        auth = FakeAuthService(isSignedIn = false, latency = kotlin.time.Duration.ZERO)
        diagnostics = RecordingLoginDiagnosticsReporter()
        clock = MutableClock()
        sut = LoginViewModel(auth, diagnostics, FakeAnalyticsTracker(), clock)
    }

    private fun TestScope.sendCodeTo(input: String = "0532 123 45 67") {
        sut.onPhoneTextChange(input)
        sut.sendCode()
        advanceUntilIdle()
    }

    // --- phone step ---

    @Test
    fun `onPhoneTextChange formats input and enables sending only for a complete number`() {
        sut.onPhoneTextChange("+90 532 123")
        assertEquals("532 123", sut.uiState.value.phoneText)
        assertFalse(sut.uiState.value.canSendCode)

        sut.onPhoneTextChange("05321234567")
        assertEquals("532 123 45 67", sut.uiState.value.phoneText)
        assertTrue(sut.uiState.value.canSendCode)
    }

    @Test
    fun `sendCode when success shows the code step with a 60 second cooldown`() = runTest {
        sut.onPhoneTextChange("5321234567")

        sut.uiState.test {
            assertEquals(LoginStep.Phone, awaitItem().step)
            sut.sendCode()
            assertTrue(awaitItem().isWorking)
            val sent = awaitItem()
            assertEquals(LoginStep.Code(requireNotNull(number)), sent.step)
            assertFalse(sent.isWorking)
            assertEquals(clock.now.plusSeconds(60), sent.resendAvailableAt)
        }
        assertEquals(listOf("+905321234567"), auth.sentCodeNumbers)
        assertEquals(60, sut.resendCooldownRemaining())
    }

    @Test
    fun `sendCode again for the same number within the cooldown sends no new SMS`() = runTest {
        sendCodeTo()
        sut.changeNumber()
        assertEquals(LoginStep.Phone, sut.uiState.value.step)

        clock.advance(Duration.ofSeconds(30))
        sut.sendCode()
        advanceUntilIdle()

        assertEquals(1, auth.sentCodeNumbers.size)
        assertTrue(sut.uiState.value.step is LoginStep.Code)
        assertEquals(30, sut.resendCooldownRemaining())
    }

    @Test
    fun `sendCode for a different number sends right away`() = runTest {
        sendCodeTo()
        sut.changeNumber()

        sendCodeTo("5551112233")

        assertEquals(listOf("+905321234567", "+905551112233"), auth.sentCodeNumbers)
    }

    // --- resend ---

    @Test
    fun `resendCode is blocked during the cooldown and sends after it`() = runTest {
        sendCodeTo()

        clock.advance(Duration.ofSeconds(59))
        assertFalse(sut.canResendCode())
        sut.resendCode()
        advanceUntilIdle()
        assertEquals(1, auth.sentCodeNumbers.size)

        clock.advance(Duration.ofSeconds(1))
        assertTrue(sut.canResendCode())
        sut.resendCode()
        advanceUntilIdle()
        assertEquals(2, auth.sentCodeNumbers.size)
        assertEquals(60, sut.resendCooldownRemaining())
    }

    @Test
    fun `resendCooldownRemaining rounds partial seconds up`() = runTest {
        sendCodeTo()

        clock.advance(Duration.ofMillis(59_500))

        assertEquals(1, sut.resendCooldownRemaining())
    }

    // --- code step ---

    @Test
    fun `onCodeChange keeps six digits and verifies automatically on the sixth`() = runTest {
        sendCodeTo()

        sut.onCodeChange("11 11 1")
        assertEquals("11111", sut.uiState.value.codeText)
        assertFalse(sut.uiState.value.canVerify)

        sut.onCodeChange("1111119")
        assertTrue(sut.uiState.value.isWorking)
        advanceUntilIdle()

        assertEquals("+905321234567", auth.currentUser?.phoneNumber)
        assertTrue(diagnostics.reports.isEmpty())
    }

    @Test
    fun `verify when code is wrong clears the code, shows the error and reports it`() = runTest {
        sendCodeTo()

        sut.onCodeChange("123456")
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isWorking)
        assertEquals("", state.codeText)
        assertEquals(AuthError.InvalidCode.userMessage, state.error)
        val report = diagnostics.reports.single()
        assertEquals("invalid_code", report.reason)
        assertEquals(LoginFailureStage.VERIFY, report.stage)
        assertEquals("+905321234567", report.phoneNumber)
        assertNull(report.backendCode)
    }

    @Test
    fun `verify when code expired allows an immediate new code`() = runTest {
        sendCodeTo()
        auth.signInFailure = AuthError.CodeExpired

        sut.onCodeChange("111111")
        advanceUntilIdle()

        assertNull(sut.uiState.value.resendAvailableAt)
        assertTrue(sut.canResendCode())
        sut.changeNumber()
        sut.sendCode()
        advanceUntilIdle()
        assertEquals(2, auth.sentCodeNumbers.size)
    }

    @Test
    fun `typing after an error clears it`() = runTest {
        sendCodeTo()
        sut.onCodeChange("123456")
        advanceUntilIdle()

        sut.onCodeChange("1")

        assertNull(sut.uiState.value.error)
    }

    // --- failures and support ---

    @Test
    fun `sendCode when sms is blocked reports error 39 and prefills support context`() = runTest {
        auth.sendFailure = AuthError.SmsTemporarilyUnavailable

        sendCodeTo()

        val state = sut.uiState.value
        assertEquals(LoginStep.Phone, state.step)
        assertEquals(AuthError.SmsTemporarilyUnavailable.userMessage, state.error)
        val report = diagnostics.reports.single()
        assertEquals(39, report.backendCode)
        assertEquals(LoginFailureStage.SEND_CODE, report.stage)
        assertEquals(clock.now, report.occurredAt)
        assertEquals(
            "Giriş sorunu bildirimi\nNumara: +905321234567\nAşama: Kod gönderme\n" +
                "Sebep: sms_temporarily_unavailable (Error code: 39)",
            sut.supportContext(),
        )
    }

    @Test
    fun `supportContext without a failure carries only the typed number`() {
        assertNull(sut.supportContext())

        sut.onPhoneTextChange("5321234567")

        assertEquals("Giriş sorunu bildirimi\nNumara: +905321234567", sut.supportContext())
    }

    @Test
    fun `onContactSupport emits the support e-mail event`() = runTest {
        sut.events.test {
            sut.onContactSupport()
            assertEquals(LoginEvent.ContactSupport("Giriş ekranı"), awaitItem())
        }
    }

    @Test
    fun `input is ignored while a request runs`() = runTest {
        sut.onPhoneTextChange("5321234567")
        sut.sendCode()

        sut.onPhoneTextChange("5551112233")

        assertEquals("532 123 45 67", sut.uiState.value.phoneText)
        advanceUntilIdle()
    }

    // --- session ---

    @Test
    fun `sign out after a successful sign-in starts over on an empty number step`() = runTest {
        sendCodeTo()
        sut.onCodeChange(FakeAuthService.VALID_CODE)
        advanceUntilIdle()
        assertTrue(auth.currentUser != null)
        // Stays busy while signed in: the session gate replaces the login screen.
        assertTrue(sut.uiState.value.isWorking)

        auth.signOut()
        advanceUntilIdle()

        assertEquals(LoginUiState(), sut.uiState.value)
        assertEquals(0, sut.resendCooldownRemaining())
        assertNull(sut.supportContext())
    }
}
