package com.salticusteam.vifi.feature.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.AppUpdate
import com.salticusteam.vifi.core.util.StoreLauncher

/**
 * Wraps the app's content with the update gate (iOS RootView update handling). A required update replaces
 * [content] entirely with [ForceUpdateScreen]; an optional one shows a dismissible dialog over it.
 */
@Composable
fun AppUpdateGate(
    modifier: Modifier = Modifier,
    viewModel: AppUpdateViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    AppUpdateGateContent(
        state = state,
        onOpenStore = { update -> StoreLauncher.openStore(context, update.storeUrl) },
        onOptionalDismissed = viewModel::onOptionalDismissed,
        modifier = modifier,
        onOptionalUpdateChosen = viewModel::onOptionalUpdateChosen,
        onOptionalLaterChosen = viewModel::onOptionalLaterChosen,
        content = content,
    )
}

/** Stateless gate; previews and tests drive this. */
@Composable
fun AppUpdateGateContent(
    state: AppUpdateUiState,
    onOpenStore: (AppUpdate) -> Unit,
    onOptionalDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    /** Analytics hooks of the optional prompt's two answers; the dismissal itself goes to [onOptionalDismissed]. */
    onOptionalUpdateChosen: () -> Unit = {},
    onOptionalLaterChosen: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val required = state.required
    if (required != null) {
        // The gate replaces the UI rather than overlaying it: nothing behind it stays composed or interactive.
        ForceUpdateScreen(update = required, onUpdate = { onOpenStore(required) }, modifier = modifier)
        return
    }

    content()

    state.optional?.let { update ->
        OptionalUpdateDialog(
            update = update,
            onUpdate = {
                onOptionalUpdateChosen()
                onOpenStore(update)
                onOptionalDismissed()
            },
            onLater = {
                onOptionalLaterChosen()
                onOptionalDismissed()
            },
        )
    }
}

/** The dismissible "new version available" suggestion (iOS optional-update alert). */
@Composable
private fun OptionalUpdateDialog(
    update: AppUpdate,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        modifier = Modifier.testTag("optionalUpdate.dialog"),
        title = { Text(stringResource(R.string.optional_update_title)) },
        text = { Text(update.message ?: stringResource(R.string.optional_update_default_message, update.version)) },
        confirmButton = {
            TextButton(onClick = onUpdate, modifier = Modifier.testTag("optionalUpdate.update")) {
                Text(stringResource(R.string.update_action_update))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, modifier = Modifier.testTag("optionalUpdate.later")) {
                Text(stringResource(R.string.optional_update_later))
            }
        },
    )
}
