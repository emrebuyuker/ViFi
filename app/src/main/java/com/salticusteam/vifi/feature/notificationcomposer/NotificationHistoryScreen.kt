package com.salticusteam.vifi.feature.notificationcomposer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationAdminService
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationStatus
import com.salticusteam.vifi.core.model.SentNotification
import java.time.Instant

private const val HISTORY_TAG = "$COMPOSER_TAG.history"

/**
 * The admin's notification history (iOS `NotificationHistoryView`): what was sent, scheduled or sent automatically,
 * with delivery numbers, and a way to cancel a scheduled one. Opened from the composer's "Geçmiş".
 */
@Composable
fun NotificationHistoryScreen(onBack: () -> Unit, viewModel: NotificationHistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onScreenShown() }

    NotificationHistoryContent(
        state = state,
        onRefresh = viewModel::refresh,
        onCancel = viewModel::cancel,
        onBack = onBack,
    )
}

/** Stateless history; previews and UI tests drive this. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryContent(
    state: NotificationHistoryUiState,
    onRefresh: () -> Unit,
    onCancel: (SentNotification) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ViFiTopAppBar(title = stringResource(R.string.notification_history_title), onBack = onBack) },
    ) { padding ->
        val notifications = state.notifications
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        when {
            notifications == null && state.loadError != null -> ErrorView(state.loadError, onRefresh, contentModifier)
            notifications == null -> LoadingView(contentModifier, title = stringResource(R.string.notification_history_loading))
            else -> PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = contentModifier) {
                if (notifications.isEmpty() && state.errorMessage == null) {
                    EmptyView(
                        title = stringResource(R.string.notification_history_empty_title),
                        icon = Icons.Filled.NotificationsOff,
                        message = stringResource(R.string.notification_history_empty_message),
                    )
                } else {
                    HistoryList(state = state, notifications = notifications, onCancel = onCancel)
                }
            }
        }
    }
}

@Composable
private fun HistoryList(
    state: NotificationHistoryUiState,
    notifications: List<SentNotification>,
    onCancel: (SentNotification) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("$HISTORY_TAG.list")) {
        state.errorMessage?.let { message ->
            item(key = "error") {
                StatusLine(
                    icon = Icons.Filled.Error,
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("$HISTORY_TAG.error"),
                )
            }
        }
        items(items = notifications, key = { it.id }) { notification ->
            HistoryRow(
                notification = notification,
                canCancel = state.canCancel(notification),
                onCancel = { onCancel(notification) },
            )
            HorizontalDivider(Modifier.padding(start = 70.dp))
        }
    }
}

/** One entry: title, audience, status and badges, and the delivery numbers once it has gone out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryRow(notification: SentNotification, canCancel: Boolean, onCancel: () -> Unit) {
    val rowTag = "$HISTORY_TAG.row.${notification.id}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconBadge(icon = notification.kind.historyIcon)
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {}
                .testTag(rowTag),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(text = notification.title, style = MaterialTheme.typography.titleMedium)
            if (notification.audienceLabel.isNotEmpty()) {
                Text(
                    text = notification.audienceLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Wraps instead of squeezing at large font scales.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HistoryBadge(stringResource(notification.status.adminLabelRes), notification.status.badgeColor)
                HistoryBadge(stringResource(notification.kind.adminLabelRes), MaterialTheme.colorScheme.surfaceContainerHigh)
                if (notification.isTest) {
                    HistoryBadge(stringResource(R.string.notification_history_badge_test), MaterialTheme.colorScheme.secondaryContainer)
                }
                if (notification.isAutomatic) {
                    HistoryBadge(
                        stringResource(R.string.notification_history_badge_automatic),
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
            timeLine(notification)?.let { line ->
                Text(text = line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (notification.hasDelivered) StatsLines(notification)
        }
        if (notification.status == NotificationStatus.SCHEDULED) {
            CancelMenu(rowTag = rowTag, enabled = canCancel, onCancel = onCancel)
        }
    }
}

/** The row's menu (iOS swipe action and context menu): "İptal et", with no confirmation (iOS parity). */
@Composable
private fun CancelMenu(rowTag: String, enabled: Boolean, onCancel: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("$rowTag.menu")) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_notification_history_actions))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.notification_history_cancel), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Filled.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                enabled = enabled,
                onClick = {
                    expanded = false
                    onCancel()
                },
                modifier = Modifier.testTag("$rowTag.cancel"),
            )
        }
    }
}

/** A small status pill: onSurface text on a container colour keeps the contrast in both themes. */
@Composable
private fun HistoryBadge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(color, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun StatsLines(notification: SentNotification) {
    val stats = notification.stats
    Column {
        Text(
            text = pluralStringResource(
                R.plurals.notification_history_delivery_stats,
                stats.recipients,
                stats.recipients,
                stats.pushSent,
                stats.pushFailed,
            ),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = pluralStringResource(
                R.plurals.notification_history_engagement_stats,
                stats.opened,
                stats.opened,
                stats.actionTapped,
            ),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** When it goes out (scheduled) or went out, with a note when quiet hours moved it. */
@Composable
private fun timeLine(notification: SentNotification): String? = when (notification.status) {
    NotificationStatus.SCHEDULED -> notification.scheduledAt?.let { at ->
        val time = at.toComposerDateTime()
        if (notification.deferredByQuietHours) {
            stringResource(R.string.notification_history_scheduled_at_quiet_hours, time)
        } else {
            stringResource(R.string.notification_history_scheduled_at, time)
        }
    }
    NotificationStatus.SENT, NotificationStatus.SENDING, NotificationStatus.FAILED ->
        (notification.sentAt ?: notification.createdAt)?.toComposerDateTime()
    NotificationStatus.CANCELED -> notification.createdAt?.toComposerDateTime()
}

/** Pushes and opens only mean something once the notification has gone out. */
private val SentNotification.hasDelivered: Boolean
    get() = when (status) {
        NotificationStatus.SENT, NotificationStatus.SENDING, NotificationStatus.FAILED -> true
        NotificationStatus.SCHEDULED, NotificationStatus.CANCELED -> false
    }

private val NotificationKind.historyIcon: ImageVector
    get() = when (this) {
        NotificationKind.GENERAL -> Icons.Filled.Campaign
        NotificationKind.PROMO -> Icons.Filled.LocalOffer
        NotificationKind.SYSTEM -> Icons.Filled.NotificationsActive
    }

/** Theme container colours (no fixed colours): Zamanlandı orange, Gönderildi green-olive, Başarısız red. */
private val NotificationStatus.badgeColor: Color
    @Composable get() = when (this) {
        NotificationStatus.SCHEDULED -> MaterialTheme.colorScheme.primaryContainer
        NotificationStatus.SENDING -> MaterialTheme.colorScheme.surfaceContainerHighest
        NotificationStatus.SENT -> MaterialTheme.colorScheme.tertiaryContainer
        NotificationStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        NotificationStatus.CANCELED -> MaterialTheme.colorScheme.surfaceVariant
    }

@ViFiThemePreviews
@Composable
private fun NotificationHistoryContentPreview() {
    val scheduled = FakeNotificationAdminService.SAMPLE_HISTORY.first().copy(
        id = "sample-scheduled",
        status = NotificationStatus.SCHEDULED,
        scheduledAt = Instant.ofEpochSecond(1_761_800_000),
        isTest = true,
    )
    ViFiTheme {
        NotificationHistoryContent(
            state = NotificationHistoryUiState(notifications = listOf(scheduled) + FakeNotificationAdminService.SAMPLE_HISTORY),
            onRefresh = {},
            onCancel = {},
            onBack = {},
        )
    }
}

@ViFiThemePreviews
@Composable
private fun NotificationHistoryEmptyPreview() {
    ViFiTheme {
        NotificationHistoryContent(
            state = NotificationHistoryUiState(notifications = emptyList()),
            onRefresh = {},
            onCancel = {},
            onBack = {},
        )
    }
}
