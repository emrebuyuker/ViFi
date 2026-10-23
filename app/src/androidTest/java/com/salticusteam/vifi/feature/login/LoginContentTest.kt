package com.salticusteam.vifi.feature.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.AuthError
import com.salticusteam.vifi.core.model.PhoneNumber
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val codeStep = LoginStep.Code(requireNotNull(PhoneNumber.fromNational("5321234567")))

    private var typedPhone: String? = null
    private var typedCode: String? = null
    private var sendClicks = 0
    private var verifyClicks = 0
    private var resendClicks = 0
    private var changeNumberClicks = 0
    private var contactClicks = 0

    private fun show(state: LoginUiState, resendSecondsRemaining: Int = 0) {
        composeRule.setContent {
            ViFiTheme {
                LoginContent(
                    state = state,
                    resendSecondsRemaining = resendSecondsRemaining,
                    onPhoneTextChange = { typedPhone = it },
                    onSendCode = { sendClicks++ },
                    onCodeChange = { typedCode = it },
                    onVerify = { verifyClicks++ },
                    onResend = { resendClicks++ },
                    onChangeNumber = { changeNumberClicks++ },
                    onContactSupport = { contactClicks++ },
                )
            }
        }
    }

    @Test
    fun phoneStep_incompleteNumber_disablesSendCode() {
        show(LoginUiState(phoneText = "532 123"))

        composeRule.onNodeWithTag("login.sendCode").assertIsNotEnabled()
        composeRule.onNodeWithTag("login.phone").performTextInput("4")

        assertEquals("532 1234", typedPhone)
    }

    @Test
    fun phoneStep_completeNumber_sendsCodeAndContactsSupport() {
        show(LoginUiState(phoneText = "532 123 45 67"))

        composeRule.onNodeWithTag("login.sendCode").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("login.contact").performClick()

        assertEquals(1, sendClicks)
        assertEquals(1, contactClicks)
    }

    @Test
    fun phoneStep_error_isShown() {
        show(LoginUiState(phoneText = "532 123 45 67", error = AuthError.SmsTemporarilyUnavailable.userMessage))

        composeRule.onNodeWithTag("login.error").assertIsDisplayed()
    }

    @Test
    fun codeStep_duringCooldown_disablesResendAndShowsCountdown() {
        show(LoginUiState(step = codeStep, codeText = "123"), resendSecondsRemaining = 42)

        composeRule.onNodeWithTag("login.resend").assertIsNotEnabled().assertTextContains("42", substring = true)
        composeRule.onNodeWithTag("login.verify").assertIsNotEnabled()
        composeRule.onNodeWithTag("login.code").performTextInput("4")

        assertEquals("1234", typedCode)
    }

    @Test
    fun codeStep_completeCode_verifiesResendsAndChangesNumber() {
        show(LoginUiState(step = codeStep, codeText = "123456"))

        composeRule.onNodeWithTag("login.verify").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("login.resend").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("login.changeNumber").performClick()

        assertEquals(1, verifyClicks)
        assertEquals(1, resendClicks)
        assertEquals(1, changeNumberClicks)
    }

    @Test
    fun codeStep_working_disablesEverything() {
        show(LoginUiState(step = codeStep, codeText = "123456", isWorking = true))

        composeRule.onNodeWithTag("login.code").assertIsNotEnabled()
        composeRule.onNodeWithTag("login.verify").assertIsNotEnabled()
        composeRule.onNodeWithTag("login.changeNumber").assertIsNotEnabled()
    }
}
