package com.salticusteam.vifi.feature.login

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.BuildConfig
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.AuthError
import com.salticusteam.vifi.core.model.PhoneNumber
import com.salticusteam.vifi.core.util.ContactSupport
import kotlinx.coroutines.delay

/**
 * The sign-in screen, shown whenever nobody is signed in (iOS LoginView): phone number, then the SMS code.
 * Success needs no navigation: the session gate in AppRoot replaces this screen.
 */
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showNoMailDialog by rememberSaveable { mutableStateOf(false) }

    // Counts the resend cooldown down once a second (iOS TimelineView); stops at zero.
    var resendSecondsRemaining by remember { mutableIntStateOf(viewModel.resendCooldownRemaining()) }
    LaunchedEffect(state.resendAvailableAt) {
        resendSecondsRemaining = viewModel.resendCooldownRemaining()
        while (resendSecondsRemaining > 0) {
            delay(1_000)
            resendSecondsRemaining = viewModel.resendCooldownRemaining()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.ContactSupport -> {
                    val uri = ContactSupport.mailtoUri(BuildConfig.VERSION_NAME, Build.VERSION.RELEASE, event.context)
                    try {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(uri)))
                    } catch (e: ActivityNotFoundException) {
                        showNoMailDialog = true
                    }
                }
            }
        }
    }

    LoginContent(
        state = state,
        resendSecondsRemaining = resendSecondsRemaining,
        onPhoneTextChange = viewModel::onPhoneTextChange,
        onSendCode = viewModel::sendCode,
        onCodeChange = viewModel::onCodeChange,
        onVerify = viewModel::verifyCode,
        onResend = viewModel::resendCode,
        onChangeNumber = viewModel::changeNumber,
        onContactSupport = viewModel::onContactSupport,
    )

    if (showNoMailDialog) {
        NoMailAppDialog(onDismiss = { showNoMailDialog = false })
    }
}

/** Stateless login content: the header and the current step. Previews and UI tests drive this. */
@Composable
fun LoginContent(
    state: LoginUiState,
    resendSecondsRemaining: Int,
    onPhoneTextChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onChangeNumber: () -> Unit,
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .padding(horizontal = 24.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LoginHeader(step = state.step)
                AnimatedContent(targetState = state.step is LoginStep.Code, label = "loginStep") { isCodeStep ->
                    if (isCodeStep) {
                        CodeStep(
                            state = state,
                            resendSecondsRemaining = resendSecondsRemaining,
                            onCodeChange = onCodeChange,
                            onVerify = onVerify,
                            onResend = onResend,
                            onChangeNumber = onChangeNumber,
                        )
                    } else {
                        PhoneStep(
                            state = state,
                            onPhoneTextChange = onPhoneTextChange,
                            onSendCode = onSendCode,
                            onContactSupport = onContactSupport,
                        )
                    }
                }
            }
        }
    }
}

/** Logo and the step's title and explanation. */
@Composable
private fun LoginHeader(step: LoginStep) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.mipmap.icon),
            contentDescription = null, // decorative; the title carries the meaning
            modifier = Modifier
                .padding(bottom = 6.dp)
                .size(84.dp)
                .clip(RoundedCornerShape(19.dp)),
        )
        val (title, description) = when (step) {
            LoginStep.Phone ->
                stringResource(R.string.auth_phone_title) to stringResource(R.string.auth_phone_description)
            is LoginStep.Code -> stringResource(R.string.auth_code_title) to
                stringResource(R.string.auth_code_description, step.phoneNumber.formatted)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { heading() }
                .testTag("login.title"),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** A full-width primary button that shows a spinner instead of its title while working. */
@Composable
internal fun PrimaryActionButton(
    title: String,
    isWorking: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val workingDescription = stringResource(R.string.cd_auth_working)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (isWorking) contentDescription = workingDescription },
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Kept for layout while the spinner shows, so the button does not change height.
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.alpha(if (isWorking) 0f else 1f),
            )
            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** The current error under the field, in the error color with a warning icon. */
@Composable
internal fun LoginErrorMessage(message: String?) {
    if (message == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("login.error"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = null, // the message itself is read out
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Shown when no app can open the support e-mail: the address to write to, with a copy action. */
@Composable
private fun NoMailAppDialog(onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contact_support_no_mail_title)) },
        text = { Text(stringResource(R.string.contact_support_no_mail_message, ContactSupport.EMAIL)) },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(ContactSupport.EMAIL))
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.contact_support_copy_address_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun LoginPhonePreview() {
    ViFiTheme {
        LoginContent(
            state = LoginUiState(phoneText = "532 123 45", error = AuthError.SmsTemporarilyUnavailable.userMessage),
            resendSecondsRemaining = 0,
            onPhoneTextChange = {}, onSendCode = {}, onCodeChange = {}, onVerify = {}, onResend = {},
            onChangeNumber = {}, onContactSupport = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginCodePreview() {
    val phoneNumber = remember { PhoneNumber.fromNational("5321234567") }
    ViFiTheme {
        LoginContent(
            state = LoginUiState(step = phoneNumber?.let(LoginStep::Code) ?: LoginStep.Phone, codeText = "123"),
            resendSecondsRemaining = 42,
            onPhoneTextChange = {}, onSendCode = {}, onCodeChange = {}, onVerify = {}, onResend = {},
            onChangeNumber = {}, onContactSupport = {},
        )
    }
}
