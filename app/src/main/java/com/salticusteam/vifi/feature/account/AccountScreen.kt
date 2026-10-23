package com.salticusteam.vifi.feature.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme

/**
 * The signed-in account (iOS AccountView), opened from the home screen: the profile (opens the editor), the
 * verified phone number and signing out. Account deletion (ACC-03), the coin balance and moderation join later.
 */
@Composable
fun AccountScreen(
    onEditProfile: () -> Unit,
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onScreenShown()
    }

    AccountContent(
        state = state,
        onEditProfile = onEditProfile,
        onSignOut = viewModel::signOut,
        onBack = onBack,
    )
}

/** Stateless account content; previews and UI tests drive this. */
@Composable
fun AccountContent(
    state: AccountUiState,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isConfirmingSignOut by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ViFiTopAppBar(title = stringResource(R.string.account_title), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("account.list"),
        ) {
            item(key = "profile") {
                ProfileRow(profileName = state.profileName, onClick = onEditProfile)
                HorizontalDivider()
            }
            item(key = "phone") {
                PhoneRow(formattedPhoneNumber = state.formattedPhoneNumber)
                HorizontalDivider()
            }
            item(key = "signOut") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.account_sign_out_button)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,
                        headlineColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .clickable(role = Role.Button) { isConfirmingSignOut = true }
                        .testTag("account.signOut"),
                )
            }
        }
    }

    if (isConfirmingSignOut) {
        SignOutDialog(
            onConfirm = {
                isConfirmingSignOut = false
                onSignOut()
            },
            onDismiss = { isConfirmingSignOut = false },
        )
    }
}

/** "Profil" with the user's name; opens the profile editor. */
@Composable
private fun ProfileRow(profileName: String?, onClick: () -> Unit) {
    ListItem(
        overlineContent = { Text(stringResource(R.string.account_profile_label)) },
        headlineContent = {
            Text(
                text = profileName ?: stringResource(R.string.account_edit_profile),
                fontWeight = FontWeight.SemiBold,
            )
        },
        leadingContent = { IconBadge(icon = Icons.Filled.Badge) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("account.editProfile"),
    )
}

/** The verified number, read-only, in tabular digits. */
@Composable
private fun PhoneRow(formattedPhoneNumber: String?) {
    ListItem(
        overlineContent = { Text(stringResource(R.string.account_phone_label)) },
        headlineContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = formattedPhoneNumber ?: stringResource(R.string.account_phone_missing),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.profile_phone_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = { IconBadge(icon = Icons.Filled.AccountCircle) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        // Read as one element by TalkBack (iOS `.accessibilityElement(children: .combine)`).
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .testTag("account.phone"),
    )
}

@Composable
private fun SignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_sign_out_dialog_title)) },
        text = { Text(stringResource(R.string.account_sign_out_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("account.signOut.confirm")) {
                Text(stringResource(R.string.account_sign_out_button), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("account.signOut.cancel")) {
                Text(stringResource(R.string.account_sign_out_dialog_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun AccountContentPreview() {
    ViFiTheme {
        AccountContent(
            state = AccountUiState(profileName = "Ayşe Yılmaz", formattedPhoneNumber = "+90 532 123 45 67"),
            onEditProfile = {},
            onSignOut = {},
            onBack = {},
        )
    }
}
