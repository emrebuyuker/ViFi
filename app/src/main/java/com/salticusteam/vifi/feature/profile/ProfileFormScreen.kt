package com.salticusteam.vifi.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.CatalogSelection
import com.salticusteam.vifi.core.model.Gender

/**
 * The profile form screen, stateless: the completion gate (no back, an intro and a sign-out action) and the edit
 * screen (back arrow) both render it. While a catalog picker is open it replaces the form; back closes it.
 */
@Composable
fun ProfileFormContent(
    state: ProfileFormUiState,
    title: String,
    onNameChange: (String) -> Unit,
    onSurnameChange: (String) -> Unit,
    onPickerOpen: (CatalogField) -> Unit,
    onPickerDismiss: () -> Unit,
    onCatalogSelected: (CatalogSelection) -> Unit,
    onGenderSelected: (Gender) -> Unit,
    onSave: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    /** The edit screen's back action; `null` on the completion gate, which cannot be left except by signing out. */
    onBack: (() -> Unit)? = null,
    /** The completion gate's sign-out action; `null` on the edit screen. */
    onSignOut: (() -> Unit)? = null,
) {
    val picker = state.picker
    BackHandler(enabled = picker != null, onBack = onPickerDismiss)

    if (picker != null) {
        CatalogPicker(
            title = picker.label(),
            options = state.options(picker),
            selection = state.selection(picker),
            testTagPrefix = picker.testTag,
            onSelect = onCatalogSelected,
            onBack = onPickerDismiss,
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViFiTopAppBar(
                title = title,
                onBack = onBack,
                actions = { SaveAction(state = state, onSave = onSave, testTag = "$testTagPrefix.save") },
            )
        },
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
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (onSignOut != null) CompletionHeader()
                ProfileFormFields(
                    state = state,
                    onNameChange = onNameChange,
                    onSurnameChange = onSurnameChange,
                    onPickerOpen = onPickerOpen,
                    onGenderSelected = onGenderSelected,
                )
                if (onSignOut != null) {
                    TextButton(
                        onClick = onSignOut,
                        enabled = !state.isWorking,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .testTag("profile.signOut"),
                    ) {
                        Text(stringResource(R.string.profile_sign_out_button), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** "Kaydet", or a spinner while the save runs. */
@Composable
private fun SaveAction(state: ProfileFormUiState, onSave: () -> Unit, testTag: String) {
    if (state.isWorking) {
        val workingDescription = stringResource(R.string.cd_profile_saving)
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .size(24.dp)
                .semantics { contentDescription = workingDescription }
                .testTag("$testTag.progress"),
        )
    } else {
        TextButton(onClick = onSave, enabled = state.canSave, modifier = Modifier.testTag(testTag)) {
            Text(stringResource(R.string.profile_save_button))
        }
    }
}

@Composable
private fun CompletionHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.profile_completion_headline),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.profile_completion_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileCompletionPreview() {
    ViFiTheme {
        ProfileFormContent(
            state = ProfileFormUiState(phoneNumber = "+905321234567"),
            title = "Profilini Tamamla",
            onNameChange = {},
            onSurnameChange = {},
            onPickerOpen = {},
            onPickerDismiss = {},
            onCatalogSelected = {},
            onGenderSelected = {},
            onSave = {},
            testTagPrefix = "profile",
            onSignOut = {},
        )
    }
}
