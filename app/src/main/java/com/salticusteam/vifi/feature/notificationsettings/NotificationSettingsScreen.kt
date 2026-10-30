package com.salticusteam.vifi.feature.notificationsettings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsState
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.feature.notifications.openAppNotificationSettings

/**
 * Account › Bildirim Ayarları (iOS NotificationSettingsView): whether the system lets ViFi show notifications, with the
 * way to turn them on ("Bildirimleri Aç" while the system dialog can still be shown, "Ayarlar'ı Aç" after that), and
 * the "Kampanya ve fırsat bildirimleri" switch, the in-app way to withdraw the campaign consent. The switch is bound
 * live to the consent record and is off until the user turns it on.
 */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.onScreenShown()
    }

    // Opened, or back from the system settings: the permission may have changed (iOS `.task` + scenePhase `.active`).
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }

    NotificationSettingsContent(
        state = state,
        onEnable = viewModel::onEnable,
        onOpenSettings = { context.openAppNotificationSettings() },
        onMarketingChange = viewModel::onMarketingChange,
        onBack = onBack,
    )
}

/** Stateless settings content; previews and UI tests drive this. */
@Composable
fun NotificationSettingsContent(
    state: NotificationSettingsUiState,
    onEnable: () -> Unit,
    onOpenSettings: () -> Unit,
    onMarketingChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViFiTopAppBar(title = stringResource(R.string.notification_settings_title), onBack = onBack)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("notificationSettings.list"),
        ) {
            item(key = "permission") {
                PermissionRow(label = state.permissionLabel)
                when {
                    state.canRequestPermission -> ActionRow(
                        text = R.string.notification_settings_enable,
                        enabled = !state.isRequestingPermission,
                        tag = "notificationSettings.enable",
                        onClick = onEnable,
                    )
                    state.needsSettingsApp -> ActionRow(
                        text = R.string.notification_settings_open_settings,
                        enabled = true,
                        tag = "notificationSettings.openSettings",
                        onClick = onOpenSettings,
                    )
                    // Allowed, or not read yet: nothing to offer.
                    else -> Unit
                }
                state.permissionNote?.let { note ->
                    Footnote(text = stringResource(note), modifier = Modifier.testTag("notificationSettings.permissionNote"))
                }
                HorizontalDivider()
            }
            item(key = "marketing") {
                MarketingRow(
                    checked = state.marketingEnabled,
                    enabled = state.canChangeMarketing,
                    onCheckedChange = onMarketingChange,
                )
                Footnote(text = stringResource(R.string.notification_settings_marketing_footer))
                if (state.isConsentUnavailable) {
                    Footnote(
                        text = stringResource(R.string.notification_settings_marketing_unavailable),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("notificationSettings.unavailable"),
                    )
                }
            }
            if (state.saveFailed) {
                item(key = "error") {
                    HorizontalDivider()
                    SaveErrorRow()
                }
            }
        }
    }
}

/** "Bildirim izni" with the status in words; read as one element. */
@Composable
private fun PermissionRow(@StringRes label: Int) {
    ListItem(
        headlineContent = {
            Text(text = stringResource(R.string.notification_settings_permission_title), fontWeight = FontWeight.SemiBold)
        },
        leadingContent = { IconBadge(icon = Icons.Filled.NotificationsActive) },
        trailingContent = {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .testTag("notificationSettings.permission"),
    )
}

/** "Bildirimleri Aç" or "Ayarlar'ı Aç", in the accent colour (an iOS list button). */
@Composable
private fun ActionRow(@StringRes text: Int, enabled: Boolean, tag: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(text)) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            headlineColor = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(tag),
    )
}

/** "Kampanya ve fırsat bildirimleri": the whole row toggles the switch. */
@Composable
private fun MarketingRow(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.notification_settings_marketing_title)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .testTag("notificationSettings.marketing"),
    )
}

/** A section's explanation under its rows (an iOS list footer). */
@Composable
private fun Footnote(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    )
}

/** "Tercihin kaydedilemedi…" after a switch change that could not be saved. */
@Composable
private fun SaveErrorRow() {
    ListItem(
        headlineContent = { Text(stringResource(R.string.notification_settings_save_error)) },
        leadingContent = { Icon(Icons.Filled.Error, contentDescription = null) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            headlineColor = MaterialTheme.colorScheme.error,
            leadingIconColor = MaterialTheme.colorScheme.error,
        ),
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .testTag("notificationSettings.error"),
    )
}

@ViFiThemePreviews
@Composable
private fun NotificationSettingsPreview() {
    ViFiTheme {
        NotificationSettingsContent(
            state = NotificationSettingsUiState(
                permission = NotificationPermission.NOT_DETERMINED,
                systemCanAsk = true,
                consentAvailability = NotificationSettingsState.Availability.READY,
                marketingEnabled = true,
            ),
            onEnable = {},
            onOpenSettings = {},
            onMarketingChange = {},
            onBack = {},
        )
    }
}

@ViFiThemePreviews
@Composable
private fun NotificationSettingsDeniedPreview() {
    ViFiTheme {
        NotificationSettingsContent(
            state = NotificationSettingsUiState(
                permission = NotificationPermission.DENIED,
                consentAvailability = NotificationSettingsState.Availability.UNAVAILABLE,
                saveFailed = true,
            ),
            onEnable = {},
            onOpenSettings = {},
            onMarketingChange = {},
            onBack = {},
        )
    }
}
