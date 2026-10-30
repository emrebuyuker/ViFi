package com.salticusteam.vifi.feature.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.platform.LocalContext
import com.salticusteam.vifi.core.util.LegalLinks
import com.salticusteam.vifi.feature.premium.PremiumLinks
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import kotlinx.coroutines.delay
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import com.salticusteam.vifi.core.designsystem.component.formatCoins
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme

/**
 * The signed-in account (iOS AccountView), opened from the home screen: the profile (opens the editor), ViFi Premium
 * (the upgrade row, or the active status with "Aboneliği Yönet"), the coin balance (hidden for a subscriber), the
 * verified phone number, Bildirim Ayarları, signing out and deleting the account (with a re-verification of the number
 * when Firebase asks for a recent sign-in). An admin also gets the Moderasyon and Bildirim Gönder entries.
 */
@Composable
fun AccountScreen(
    onEditProfile: () -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit,
    onModeration: () -> Unit = {},
    /** Opens the admin's Bildirim Gönder form (its history is reached from there). */
    onNotificationComposer: () -> Unit = {},
    /** Opens the Premium paywall from the upgrade row; `null` hides the row (the active status still shows). */
    onOpenPaywall: (() -> Unit)? = null,
    /** Opens Bildirim Ayarları; `null` hides the row. */
    onNotificationSettings: (() -> Unit)? = null,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.onScreenShown()
    }

    // Counts the resend cooldown down once a second (iOS TimelineView); stops at zero.
    var resendSecondsRemaining by remember { mutableIntStateOf(viewModel.resendCooldownRemaining()) }
    LaunchedEffect(state.resendAvailableAt) {
        resendSecondsRemaining = viewModel.resendCooldownRemaining()
        while (resendSecondsRemaining > 0) {
            delay(1_000)
            resendSecondsRemaining = viewModel.resendCooldownRemaining()
        }
    }

    AccountContent(
        state = state,
        resendSecondsRemaining = resendSecondsRemaining,
        onEditProfile = onEditProfile,
        onSignOut = viewModel::signOut,
        onDeleteAccount = viewModel::deleteAccount,
        onCodeChange = viewModel::onCodeChange,
        onConfirmReverification = viewModel::confirmReverification,
        onResendCode = viewModel::resendCode,
        onCancelReverification = viewModel::cancelReverification,
        onBack = onBack,
        onAbout = onAbout,
        onModeration = onModeration,
        onNotificationComposer = onNotificationComposer,
        onUpgradePremium = onOpenPaywall,
        // Google Play's own subscription page: change the plan, update the payment method, or cancel.
        onManageSubscription = { LegalLinks.open(context, PremiumLinks.MANAGE_SUBSCRIPTIONS_URL) },
        onNotificationSettings = onNotificationSettings,
    )
}

/** Stateless account content; previews and UI tests drive this. */
@Composable
fun AccountContent(
    state: AccountUiState,
    resendSecondsRemaining: Int,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onCodeChange: (String) -> Unit,
    onConfirmReverification: () -> Unit,
    onResendCode: () -> Unit,
    onCancelReverification: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAbout: () -> Unit = {},
    onModeration: () -> Unit = {},
    onNotificationComposer: () -> Unit = {},
    onUpgradePremium: (() -> Unit)? = null,
    onManageSubscription: () -> Unit = {},
    onNotificationSettings: (() -> Unit)? = null,
) {
    var isConfirmingSignOut by rememberSaveable { mutableStateOf(false) }
    var isConfirmingDeletion by rememberSaveable { mutableStateOf(false) }

    // Leaving mid-deletion would strand a re-verification code with no screen to enter it (iOS disables "Bitti").
    BackHandler(enabled = state.isWorking) {}

    Scaffold(
        modifier = modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViFiTopAppBar(
                title = stringResource(R.string.account_title),
                onBack = { if (!state.isWorking) onBack() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("account.list"),
        ) {
            item(key = "profile") {
                ProfileRow(profileName = state.profileName, enabled = !state.isWorking, onClick = onEditProfile)
                HorizontalDivider()
            }
            if (state.isSubscribed) {
                item(key = "premiumActive") {
                    PremiumActiveRow()
                    ManageSubscriptionRow(enabled = !state.isWorking, onClick = onManageSubscription)
                    HorizontalDivider()
                }
            } else {
                if (onUpgradePremium != null) {
                    item(key = "premiumUpgrade") {
                        PremiumUpgradeRow(enabled = !state.isWorking, onClick = onUpgradePremium)
                        HorizontalDivider()
                    }
                }
                // A subscriber has no coin economy (iOS hides the coin section).
                item(key = "coins") {
                    CoinBalanceRow(balance = state.coinBalance)
                    HorizontalDivider()
                }
            }
            item(key = "phone") {
                PhoneRow(formattedPhoneNumber = state.formattedPhoneNumber)
                HorizontalDivider()
            }
            if (state.isAdmin) {
                item(key = "moderation") {
                    ModerationRow(enabled = !state.isWorking, onClick = onModeration)
                    HorizontalDivider()
                }
                // Next to Moderasyon, in the admin's own group (iOS `moderationSection`).
                item(key = "notificationComposer") {
                    NotificationComposerRow(enabled = !state.isWorking, onClick = onNotificationComposer)
                    HorizontalDivider()
                }
            }
            if (onNotificationSettings != null) {
                item(key = "notificationSettings") {
                    NotificationSettingsRow(enabled = !state.isWorking, onClick = onNotificationSettings)
                    HorizontalDivider()
                }
            }
            item(key = "about") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.account_about_row)) },
                    leadingContent = { IconBadge(icon = Icons.Filled.Info) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onAbout)
                        .testTag("account.about"),
                )
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
                        .clickable(enabled = !state.isWorking && !state.isSigningOut, role = Role.Button) {
                            isConfirmingSignOut = true
                        }
                        .testTag("account.signOut"),
                )
                HorizontalDivider()
            }
            item(key = "delete") {
                DeleteAccountRow(
                    isWorking = state.isWorking && !state.isReverifying,
                    enabled = !state.isWorking && !state.isReverifying && !state.isSigningOut,
                    onClick = { isConfirmingDeletion = true },
                )
            }
            if (state.isReverifying) {
                item(key = "reverification") {
                    HorizontalDivider()
                    ReverificationSection(
                        state = state,
                        resendSecondsRemaining = resendSecondsRemaining,
                        onCodeChange = onCodeChange,
                        onConfirm = onConfirmReverification,
                        onResend = onResendCode,
                        onCancel = onCancelReverification,
                    )
                }
            }
            state.error?.let { message ->
                item(key = "error") {
                    AccountErrorRow(message)
                }
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

    if (isConfirmingDeletion) {
        DeleteAccountDialog(
            onConfirm = {
                isConfirmingDeletion = false
                onDeleteAccount()
            },
            onDismiss = { isConfirmingDeletion = false },
        )
    }
}

/** "Profil" with the user's name; opens the profile editor. */
@Composable
private fun ProfileRow(profileName: String?, enabled: Boolean, onClick: () -> Unit) {
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
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag("account.editProfile"),
    )
}

/** "ViFi Premium'a Geç · Tüm sınavlara sınırsız eriş": opens the paywall (iOS `AccountPremiumSection`). */
@Composable
private fun PremiumUpgradeRow(enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(text = stringResource(R.string.account_premium_upgrade_title), fontWeight = FontWeight.SemiBold)
        },
        supportingContent = { Text(stringResource(R.string.account_premium_upgrade_subtitle)) },
        leadingContent = { IconBadge(icon = Icons.Filled.WorkspacePremium) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag("account.premium.upgrade"),
    )
}

/** "ViFi Premium · Tüm sınavlar açık"; read as one element. */
@Composable
private fun PremiumActiveRow() {
    ListItem(
        headlineContent = {
            Text(text = stringResource(R.string.account_premium_title), fontWeight = FontWeight.SemiBold)
        },
        supportingContent = { Text(stringResource(R.string.account_premium_active)) },
        leadingContent = { IconBadge(icon = Icons.Filled.WorkspacePremium) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .testTag("account.premium.active"),
    )
}

/** "Aboneliği Yönet": Google Play's subscription page, with where to manage or cancel it. */
@Composable
private fun ManageSubscriptionRow(enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.account_premium_manage)) },
        supportingContent = { Text(stringResource(R.string.account_premium_manage_footer)) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            headlineColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag("account.premium.manage"),
    )
}

/** "Coin bakiyen" with the live balance, counting between values (iOS `coinSection`); read as one element. */
@Composable
private fun CoinBalanceRow(balance: Int) {
    val shown by animateIntAsState(targetValue = balance, label = "accountCoinBalance")
    val description = stringResource(R.string.cd_coin_balance, formatCoins(balance))
    ListItem(
        overlineContent = { Text(stringResource(R.string.account_coin_balance_label)) },
        headlineContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = formatCoins(shown),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.account_coin_balance_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = { IconBadge(icon = Icons.Filled.MonetizationOn) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .testTag("account.coinBalance")
            .clearAndSetSemantics { contentDescription = description },
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

/** "Moderasyon": the admin's review queue (iOS `moderationSection`). */
@Composable
private fun ModerationRow(enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(text = stringResource(R.string.moderation_account_row_title), fontWeight = FontWeight.SemiBold)
        },
        supportingContent = { Text(stringResource(R.string.moderation_account_row_subtitle)) },
        leadingContent = { IconBadge(icon = Icons.Filled.Checklist) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag("account.moderation"),
    )
}

/** "Bildirim Gönder": the admin's announcement and campaign composer (iOS `moderationSection`). */
@Composable
private fun NotificationComposerRow(enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(text = stringResource(R.string.notification_composer_account_row_title), fontWeight = FontWeight.SemiBold)
        },
        supportingContent = { Text(stringResource(R.string.notification_composer_account_row_subtitle)) },
        leadingContent = { IconBadge(icon = Icons.Filled.Campaign) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag("account.notificationComposer"),
    )
}

/** "Bildirim Ayarları": the permission and the campaign switch (iOS `NotificationSettingsEntrySection`). */
@Composable
private fun NotificationSettingsRow(enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(text = stringResource(R.string.notification_settings_account_row_title), fontWeight = FontWeight.SemiBold)
        },
        supportingContent = { Text(stringResource(R.string.notification_settings_account_row_subtitle)) },
        leadingContent = { IconBadge(icon = Icons.Filled.NotificationsActive) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag("account.notificationSettings"),
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

/** "Hesabı Sil" in the error colour, with a spinner while the deletion runs, and its footer. */
@Composable
private fun DeleteAccountRow(isWorking: Boolean, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.account_delete_button)) },
        supportingContent = { Text(stringResource(R.string.account_delete_footer)) },
        leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
        trailingContent = {
            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .testTag("account.delete.progress"),
                    strokeWidth = 2.dp,
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            headlineColor = MaterialTheme.colorScheme.error,
            leadingIconColor = MaterialTheme.colorScheme.error,
        ),
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag("account.delete"),
    )
}

@Composable
private fun AccountErrorRow(message: String) {
    ListItem(
        headlineContent = { Text(message) },
        leadingContent = { Icon(Icons.Filled.Error, contentDescription = null) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            headlineColor = MaterialTheme.colorScheme.error,
            leadingIconColor = MaterialTheme.colorScheme.error,
        ),
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .testTag("account.error"),
    )
}

@Composable
private fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_delete_dialog_title)) },
        text = { Text(stringResource(R.string.account_delete_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("account.delete.confirm")) {
                Text(stringResource(R.string.account_delete_button), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("account.delete.cancel")) {
                Text(stringResource(R.string.account_delete_cancel))
            }
        },
    )
}

@ViFiThemePreviews
@Composable
private fun AccountContentPreview() {
    ViFiTheme {
        AccountContent(
            state = AccountUiState(profileName = "Ayşe Yılmaz", formattedPhoneNumber = "+90 532 123 45 67"),
            resendSecondsRemaining = 0,
            onEditProfile = {},
            onSignOut = {},
            onDeleteAccount = {},
            onCodeChange = {},
            onConfirmReverification = {},
            onResendCode = {},
            onCancelReverification = {},
            onBack = {},
        )
    }
}

@ViFiThemePreviews
@Composable
private fun AccountPremiumPreview() {
    ViFiTheme {
        AccountContent(
            state = AccountUiState(
                profileName = "Ayşe Yılmaz",
                formattedPhoneNumber = "+90 532 123 45 67",
                isSubscribed = true,
            ),
            resendSecondsRemaining = 0,
            onEditProfile = {},
            onSignOut = {},
            onDeleteAccount = {},
            onCodeChange = {},
            onConfirmReverification = {},
            onResendCode = {},
            onCancelReverification = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountReverificationPreview() {
    ViFiTheme {
        AccountContent(
            state = AccountUiState(
                profileName = "Ayşe Yılmaz",
                formattedPhoneNumber = "+90 532 123 45 67",
                isReverifying = true,
                codeText = "123",
                error = "Kod hatalı. SMS ile gelen 6 haneli kodu kontrol edip tekrar dene.",
            ),
            resendSecondsRemaining = 42,
            onEditProfile = {},
            onSignOut = {},
            onDeleteAccount = {},
            onCodeChange = {},
            onConfirmReverification = {},
            onResendCode = {},
            onCancelReverification = {},
            onBack = {},
        )
    }
}
