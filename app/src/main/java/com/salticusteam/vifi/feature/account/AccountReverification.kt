package com.salticusteam.vifi.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salticusteam.vifi.R

/**
 * "Numaranı Doğrula" (iOS AccountView reverificationSection): the six-digit code sent to the account's number
 * (confirmed on the sixth digit), "Doğrula ve Hesabı Sil", the resend countdown and "Vazgeç".
 */
@Composable
internal fun ReverificationSection(
    state: AccountUiState,
    resendSecondsRemaining: Int,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    // Focused when the section appears, and again after a failed attempt (the field is disabled while working).
    LaunchedEffect(state.isWorking) {
        if (!state.isWorking) focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("account.reauth"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.account_reauth_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
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
            textStyle = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag("account.reauth.code"),
        )
        Text(
            text = stringResource(
                R.string.account_reauth_footer,
                state.formattedPhoneNumber ?: stringResource(R.string.account_phone_missing),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onConfirm,
            enabled = state.canConfirmReverification,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("account.reauth.verify"),
        ) {
            if (state.isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onError,
                )
            } else {
                Text(stringResource(R.string.account_reauth_verify_button))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onResend,
                enabled = !state.isWorking && resendSecondsRemaining == 0,
                modifier = Modifier.testTag("account.reauth.resend"),
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
                onClick = onCancel,
                enabled = !state.isWorking,
                modifier = Modifier.testTag("account.reauth.cancel"),
            ) {
                Text(stringResource(R.string.account_delete_cancel))
            }
        }
    }
}
