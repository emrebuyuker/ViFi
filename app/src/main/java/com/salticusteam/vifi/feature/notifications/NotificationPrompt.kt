package com.salticusteam.vifi.feature.notifications

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R

/** Hosts the one-time permission prompt; renders nothing unless it is due. */
@Composable
fun NotificationPrompt(viewModel: NotificationPromptViewModel = hiltViewModel()) {
    val isVisible by viewModel.isVisible.collectAsStateWithLifecycle()
    if (isVisible) {
        NotificationPromptDialog(onAllow = viewModel::onAllow, onLater = viewModel::onLater)
    }
}

@Composable
internal fun NotificationPromptDialog(onAllow: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        modifier = Modifier.testTag("notificationPrompt.dialog"),
        title = { Text(stringResource(R.string.notification_prompt_title)) },
        text = { Text(stringResource(R.string.notification_prompt_message)) },
        confirmButton = {
            TextButton(onClick = onAllow, modifier = Modifier.testTag("notificationPrompt.allow")) {
                Text(stringResource(R.string.notification_prompt_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, modifier = Modifier.testTag("notificationPrompt.later")) {
                Text(stringResource(R.string.notification_prompt_later))
            }
        },
    )
}
