package com.salticusteam.vifi.feature.inbox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.Announcement
import java.time.Instant

/** The announcements inbox, opened from the home toolbar's bell or a notification tap. */
@Composable
fun InboxScreen(
    onBack: () -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.onScreenShown() }

    InboxContent(
        state = state,
        onRetry = viewModel::onRetry,
        onOpenSettings = { context.openNotificationSettings() },
        onBack = onBack,
    )
}

@Composable
internal fun InboxContent(
    state: InboxUiState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ViFiTopAppBar(title = stringResource(R.string.inbox_title), onBack = onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.showPermissionOffBanner) PermissionOffBanner(onOpenSettings)
            when {
                state.isLoading -> LoadingView()
                state.error != null -> ErrorView(message = state.error, onRetry = onRetry)
                state.items.isEmpty() -> EmptyView(
                    title = stringResource(R.string.inbox_empty_title),
                    message = stringResource(R.string.inbox_empty_message),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("inbox.list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = state.items, key = { it.announcement.id }) { item -> InboxRow(item) }
                }
            }
        }
    }
}

/** Notifications are off for the app; the system dialog cannot be shown again, so link to its settings (N4). */
@Composable
private fun PermissionOffBanner(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("inbox.permissionBanner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = stringResource(R.string.inbox_permission_off_message),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenSettings, modifier = Modifier.testTag("inbox.openSettings")) {
            Text(stringResource(R.string.inbox_permission_open_settings))
        }
    }
}

@Composable
private fun InboxRow(item: InboxItem) {
    val announcement = item.announcement
    Card(
        modifier = Modifier.fillMaxWidth().testTag("inbox.row.${announcement.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (item.isUnread) {
                    Text(
                        text = stringResource(R.string.inbox_unread_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("inbox.unread.${announcement.id}"),
                    )
                }
            }
            if (announcement.body.isNotEmpty()) {
                Text(text = announcement.body, style = MaterialTheme.typography.bodyMedium)
            }
            announcement.createdAt.toInboxDate()?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Context.openNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        // Before Android 8 there is no per-app notification page; the app's details page has the toggle.
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    }
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@ViFiThemePreviews
@Composable
private fun InboxContentPreview() {
    ViFiTheme {
        InboxContent(
            state = InboxUiState(
                isLoading = false,
                showPermissionOffBanner = true,
                items = listOf(
                    InboxItem(Announcement("a1", "Yeni sınavlar eklendi", "Bu hafta 40 yeni sınav.", Instant.parse("2026-10-20T09:00:00Z")), true),
                    InboxItem(Announcement("a0", "Hoş geldin", "Geçmiş sınavlara tek yerden ulaş.", null), false),
                ),
            ),
            onRetry = {}, onOpenSettings = {}, onBack = {},
        )
    }
}
