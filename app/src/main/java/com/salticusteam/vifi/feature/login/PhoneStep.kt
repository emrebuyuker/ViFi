package com.salticusteam.vifi.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.PhoneNumber

/** The number step (AUTH-05): `+90` prefix, the number field, "Kod Gönder", the SMS notice and "Bize Ulaş". */
@Composable
internal fun PhoneStep(
    state: LoginUiState,
    onPhoneTextChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val fieldDescription = stringResource(R.string.cd_auth_phone_field, PhoneNumber.COUNTRY_CODE)
    val send = {
        keyboard?.hide()
        onSendCode()
    }

    // Back from the code step ("Numarayı değiştir") or first shown: ready for typing.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(
            // The text is reformatted on every edit, so the caret always goes to the end (after the last digit).
            value = TextFieldValue(state.phoneText, TextRange(state.phoneText.length)),
            onValueChange = { onPhoneTextChange(it.text) },
            enabled = !state.isWorking,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge,
            prefix = {
                Text(
                    text = "${PhoneNumber.COUNTRY_CODE}  ",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            placeholder = { Text(stringResource(R.string.auth_phone_placeholder)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (state.canSendCode) send() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics { contentDescription = fieldDescription }
                .testTag("login.phone"),
        )

        LoginErrorMessage(state.error)

        PrimaryActionButton(
            title = stringResource(R.string.auth_phone_send_code_button),
            isWorking = state.isWorking,
            enabled = state.canSendCode,
            onClick = send,
            modifier = Modifier.testTag("login.sendCode"),
        )

        Text(
            text = stringResource(R.string.auth_phone_sms_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // A way out when the SMS never arrives (e.g. the backend's "Error code: 39"); the e-mail is prefilled
        // with what went wrong so support can diagnose it.
        TextButton(onClick = onContactSupport, modifier = Modifier.testTag("login.contact")) {
            Icon(Icons.Filled.Email, contentDescription = null)
            Text(
                text = stringResource(R.string.auth_contact_support_button),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
