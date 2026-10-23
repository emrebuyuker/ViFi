package com.salticusteam.vifi.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salticusteam.vifi.R

/**
 * The code step (AUTH-06): the six-digit field (submits on the sixth digit), "Doğrula", the resend countdown and
 * "Numarayı değiştir". An SMS Play services retrieves on its own signs in without this field (see AuthService).
 */
@Composable
internal fun CodeStep(
    state: LoginUiState,
    resendSecondsRemaining: Int,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onChangeNumber: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Focused when the step appears, and again after a failed attempt (the field is disabled while verifying).
    LaunchedEffect(state.isWorking) {
        if (!state.isWorking) focusRequester.requestFocus()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(
            // Digits only, so the caret always belongs after the last one (also after an autofilled code).
            value = TextFieldValue(state.codeText, TextRange(state.codeText.length)),
            onValueChange = { onCodeChange(it.text) },
            enabled = !state.isWorking,
            singleLine = true,
            label = { Text(stringResource(R.string.auth_code_label)) },
            placeholder = {
                Text(
                    text = stringResource(R.string.auth_code_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 8.sp,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag("login.code"),
        )

        LoginErrorMessage(state.error)

        PrimaryActionButton(
            title = stringResource(R.string.auth_code_verify_button),
            isWorking = state.isWorking,
            enabled = state.canVerify,
            onClick = {
                keyboard?.hide()
                onVerify()
            },
            modifier = Modifier.testTag("login.verify"),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onResend,
                enabled = !state.isWorking && resendSecondsRemaining == 0,
                modifier = Modifier.testTag("login.resend"),
            ) {
                Text(
                    text = if (resendSecondsRemaining > 0) {
                        stringResource(R.string.auth_code_resend_countdown, resendSecondsRemaining)
                    } else {
                        stringResource(R.string.auth_code_resend_button)
                    },
                )
            }
            TextButton(
                onClick = onChangeNumber,
                enabled = !state.isWorking,
                modifier = Modifier.testTag("login.changeNumber"),
            ) {
                Text(stringResource(R.string.auth_code_change_number_button))
            }
        }
    }
}
