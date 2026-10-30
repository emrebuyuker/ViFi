package com.salticusteam.vifi.feature.inbox

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.InboxItem
import com.salticusteam.vifi.core.model.InboxSource
import com.salticusteam.vifi.core.model.NotificationAction
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.util.LogCategory
import com.salticusteam.vifi.feature.browse.ExamUnlockSheet
import com.salticusteam.vifi.feature.browse.ExamUnlockViewModel
import com.salticusteam.vifi.feature.examviewer.rememberExamImageLoader
import java.time.Instant

/**
 * The notifications inbox, opened from the home toolbar's bell or a notification tap ([highlightId], the route
 * argument). Card actions open an exam (through its lock), the paywall, the add-exam form, a link or a place.
 */
@Composable
fun InboxScreen(
    highlightId: String?,
    onBack: () -> Unit,
    onOpenExam: (ArchivePath, ExamKind) -> Unit,
    onOpenPaywall: () -> Unit,
    onAddExam: (ArchivePath?) -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
    unlockViewModel: ExamUnlockViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unlockState by unlockViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.onScreenShown() }
    // A newer tap on top of the open inbox only changes the argument.
    LaunchedEffect(highlightId) { viewModel.onHighlightRequested(highlightId) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is InboxEvent.OpenExam -> onOpenExam(event.path, event.kind)
                InboxEvent.OpenPaywall -> onOpenPaywall()
                is InboxEvent.OpenAddExam -> onAddExam(event.lesson)
                is InboxEvent.OpenUrl -> context.openUrl(event.url)
                // The maps choice sheet arrives with INB-04.
                is InboxEvent.ChooseMapsApp -> Unit
            }
        }
    }

    // The gate found the exam locked: the archive's own unlock sheet takes over, its outcome comes back here.
    LaunchedEffect(state.lockedExam) { state.lockedExam?.let(unlockViewModel::onLockedExamTap) }
    LaunchedEffect(Unit) { unlockViewModel.events.collect(viewModel::onUnlockEvent) }

    InboxContent(
        state = state,
        imageLoader = rememberExamImageLoader(),
        onAction = viewModel::onAction,
        onRetry = viewModel::onRetry,
        onOpenSettings = { context.openNotificationSettings() },
        onBack = onBack,
    )

    unlockState.examToUnlock?.let { exam ->
        ExamUnlockSheet(
            examName = exam.name,
            state = unlockState,
            onUnlock = unlockViewModel::onUnlockConfirm,
            onPurchaseWithMoney = unlockViewModel::onPurchaseWithMoney,
            onEarnCoins = unlockViewModel::onEarnCoins,
            onGoPremium = unlockViewModel::onGoPremium,
            onDismiss = {
                // Closing is refused while an unlock or a purchase runs; its outcome still has to come back.
                if (!unlockState.isBusy) {
                    unlockViewModel.onSheetDismiss()
                    viewModel.onUnlockSheetDismissed()
                }
            },
        )
    }

    state.examError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::onExamErrorShown,
            title = { Text(stringResource(R.string.inbox_exam_error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::onExamErrorShown, modifier = Modifier.testTag("inbox.examError.ok")) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

@Composable
internal fun InboxContent(
    state: InboxUiState,
    imageLoader: ImageLoader?,
    onAction: (InboxEntry) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    val highlightIndex = state.highlightedId?.let { id -> state.items.indexOfFirst { it.item.id == id } }
        ?.takeIf { it >= 0 }
        ?.let { it + if (state.hasPartialFailure) 1 else 0 }
    LaunchedEffect(state.highlightedId, highlightIndex) { highlightIndex?.let { listState.scrollToCenter(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ViFiTopAppBar(title = stringResource(R.string.inbox_title), onBack = onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.showPermissionOffBanner) PermissionOffBanner(onOpenSettings)
            when {
                state.isLoading -> LoadingView()
                state.error != null -> ErrorView(message = state.error, onRetry = onRetry)
                state.items.isEmpty() && !state.hasPartialFailure -> EmptyView(
                    title = stringResource(R.string.inbox_empty_title),
                    message = stringResource(R.string.inbox_empty_message),
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("inbox.list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.hasPartialFailure) item(key = PARTIAL_FAILURE_KEY) { PartialFailureBanner(onRetry) }
                    items(items = state.items, key = { it.item.id }) { entry ->
                        val target = (entry.action as? NotificationAction.Exam)?.target
                        InboxCard(
                            entry = entry,
                            isHighlighted = entry.item.id == state.highlightedId,
                            isWorking = target != null && target.examId == state.checkingExamId,
                            imageLoader = imageLoader,
                            onAction = { onAction(entry) },
                        )
                    }
                }
            }
        }
    }
}

/** Brings the highlighted card to the middle of the list (iOS `scrollTo(anchor: .center)`). */
private suspend fun LazyListState.scrollToCenter(index: Int) {
    animateScrollToItem(index)
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val offset = (info.viewportEndOffset - info.viewportStartOffset - item.size) / 2
    if (offset > 0) animateScrollBy(-offset.toFloat())
}

/** One of the two sources failed: what loaded is listed, "Tekrar Dene" restarts both (iOS `inbox.partialFailure`). */
@Composable
private fun PartialFailureBanner(onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("inbox.partialFailure"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = stringResource(R.string.inbox_partial_failure),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry, modifier = Modifier.testTag("inbox.retry")) {
            Text(stringResource(R.string.common_try_again))
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

private fun Context.openNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        // Before Android 8 there is no per-app notification page; the app's details page has the toggle.
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    }
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** A campaign link opens in the browser (no in-app browser, iOS parity); the parser only lets https through. */
private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (e: ActivityNotFoundException) {
        LogCategory.APP.log.w(e, "No app can open the notification's link")
    }
}

private const val PARTIAL_FAILURE_KEY = "inbox.partialFailure"

@ViFiThemePreviews
@Composable
private fun InboxContentPreview() {
    ViFiTheme {
        InboxContent(
            state = InboxUiState(
                isLoading = false,
                showPermissionOffBanner = true,
                hasPartialFailure = true,
                highlightedId = "p1",
                items = listOf(
                    InboxEntry(previewItem("a1", "Yeni sınavlar eklendi", "Bu hafta 40 yeni sınav."), isUnread = true),
                    InboxEntry(
                        previewItem("p1", "Premium'da bahar fırsatı", "Kampanyalı fiyatı kaçırma.", NotificationKind.PROMO),
                        isUnread = false,
                        action = NotificationAction.Paywall,
                    ),
                ),
            ),
            imageLoader = null,
            onAction = {}, onRetry = {}, onOpenSettings = {}, onBack = {},
        )
    }
}

private fun previewItem(id: String, title: String, body: String, kind: NotificationKind = NotificationKind.GENERAL) =
    InboxItem(id, InboxSource.BROADCAST, kind, title, body, createdAt = Instant.parse("2026-10-20T09:00:00Z"))
