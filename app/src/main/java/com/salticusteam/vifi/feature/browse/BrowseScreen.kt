package com.salticusteam.vifi.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.CoinBalanceBadge
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.ExamKindBadge
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.LockBadge
import com.salticusteam.vifi.core.designsystem.component.badgeRes
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.component.childCountText
import com.salticusteam.vifi.core.designsystem.component.contentText
import com.salticusteam.vifi.core.designsystem.component.emptyListTitleRes
import com.salticusteam.vifi.core.designsystem.component.icon
import com.salticusteam.vifi.core.designsystem.component.listTitleRes
import com.salticusteam.vifi.core.designsystem.component.searchPromptRes
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamLock
import com.salticusteam.vifi.core.model.ExamSummary
import com.salticusteam.vifi.feature.inbox.InboxBadgeViewModel

/**
 * An archive list: universities on the home screen (root path) and every drill-down level (iOS HomeView /
 * BrowseView). Below the root it shows the breadcrumb trail and the toolbar's "Ana Sayfa" action.
 */
@Composable
fun BrowseScreen(
    onItemClick: (ArchiveItem) -> Unit,
    onBack: (() -> Unit)?,
    /** Opens the account screen; only the home screen passes it (the toolbar's account button). */
    onAccountClick: (() -> Unit)? = null,
    /** Opens the add-exam flow from the unlock sheet's earn branch; `null` until that flow exists. */
    onEarnCoins: (() -> Unit)? = null,
    /** Opens the inbox; only the home screen passes it (the toolbar's bell). */
    onInboxClick: (() -> Unit)? = null,
    /** Opens the about screen; only the home screen passes it (the toolbar's info button). */
    onAboutClick: (() -> Unit)? = null,
    /** Content shown above the list while it is not being searched; only the home screen passes it. */
    listHeader: (@Composable () -> Unit)? = null,
    /** Returns to an ancestor level from the breadcrumb trail; `null` hides the trail (the home screen). */
    onBreadcrumbSelect: ((ArchivePath) -> Unit)? = null,
    /** Returns to the home screen (toolbar "Ana Sayfa"); `null` hides the action (the home screen itself). */
    onHomeClick: (() -> Unit)? = null,
    viewModel: BrowseViewModel = hiltViewModel(),
    unlockViewModel: ExamUnlockViewModel = hiltViewModel(),
    deletionViewModel: ExamDeletionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lockState by unlockViewModel.uiState.collectAsStateWithLifecycle()
    val deletionState by deletionViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onScreenShown() }

    LaunchedEffect(Unit) {
        unlockViewModel.events.collect { event ->
            when (event) {
                // An unlocked exam opens through the same action as a free one.
                is ExamUnlockEvent.Unlocked -> onItemClick(event.exam)
                ExamUnlockEvent.EarnCoins -> onEarnCoins?.invoke()
            }
        }
    }

    LaunchedEffect(Unit) {
        deletionViewModel.events.collect { event ->
            when (event) {
                // Dropped at once: a refetch could still hit the cached copy of the deleted exam.
                is ExamDeletionEvent.Deleted -> viewModel.remove(event.exam)
            }
        }
    }

    BrowseContent(
        state = state,
        onSearchTextChange = viewModel::onSearchTextChange,
        onItemClick = onItemClick,
        onRetry = viewModel::load,
        onRefresh = viewModel::refresh,
        onBack = onBack,
        onAccountClick = onAccountClick,
        lockState = lockState,
        onLockedExamTap = unlockViewModel::onLockedExamTap,
        onInboxClick = onInboxClick,
        onAboutClick = onAboutClick,
        listHeader = listHeader,
        onBreadcrumbSelect = onBreadcrumbSelect,
        onHomeClick = onHomeClick,
        // Admins may delete the exams listed under a course (iOS `canDeleteExams`).
        onDeleteExam = deletionViewModel::onDeleteRequested.takeIf {
            deletionState.isAdmin && state.path.childLevel == ArchiveLevel.EXAM
        },
    )

    deletionState.examToDelete?.let { exam ->
        DeleteExamDialog(
            examName = exam.name,
            onConfirm = deletionViewModel::confirmDelete,
            onDismiss = deletionViewModel::onDeleteDismissed,
        )
    }
    deletionState.error?.let { message ->
        DeleteFailedDialog(message = message, onDismiss = deletionViewModel::onErrorDismissed)
    }

    lockState.examToUnlock?.let { exam ->
        ExamUnlockSheet(
            examName = exam.name,
            state = lockState,
            onUnlock = unlockViewModel::onUnlockConfirm,
            onEarnCoins = onEarnCoins?.let { { unlockViewModel.onEarnCoins() } },
            onDismiss = unlockViewModel::onSheetDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseContent(
    state: BrowseUiState,
    onSearchTextChange: (String) -> Unit,
    onItemClick: (ArchiveItem) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onBack: (() -> Unit)?,
    onAccountClick: (() -> Unit)? = null,
    lockState: ExamUnlockUiState = ExamUnlockUiState(),
    onLockedExamTap: (ArchiveItem) -> Unit = {},
    onInboxClick: (() -> Unit)? = null,
    onAboutClick: (() -> Unit)? = null,
    listHeader: (@Composable () -> Unit)? = null,
    /** Offers the swipe-to-delete action on each row; `null` hides it (non-admins, levels above exams). */
    onDeleteExam: ((ArchiveItem) -> Unit)? = null,
    onBreadcrumbSelect: ((ArchivePath) -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
) {
    val listedLevel = state.path.childLevel ?: ArchiveLevel.EXAM
    val title = state.path.displayName ?: stringResource(R.string.archive_home_title)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViFiTopAppBar(
                title = title,
                onBack = onBack,
                actions = {
                    CoinBalanceBadge(balance = lockState.balance, modifier = Modifier.padding(end = 4.dp))
                    if (onHomeClick != null) {
                        IconButton(onClick = onHomeClick, modifier = Modifier.testTag("toolbar.home")) {
                            Icon(
                                imageVector = Icons.Filled.Home,
                                contentDescription = stringResource(R.string.archive_home_action),
                            )
                        }
                    }
                    if (onInboxClick != null) InboxBell(onClick = onInboxClick)
                    if (onAboutClick != null) {
                        IconButton(onClick = onAboutClick, modifier = Modifier.testTag("toolbar.about")) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = stringResource(R.string.home_about_open),
                            )
                        }
                    }
                    if (onAccountClick != null) {
                        IconButton(onClick = onAccountClick, modifier = Modifier.testTag("toolbar.account")) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = stringResource(R.string.cd_account_open),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Shown in every state, so the user can step back up even when this level fails to load.
            if (onBreadcrumbSelect != null && !state.path.isRoot) {
                BreadcrumbBar(path = state.path, onSelect = onBreadcrumbSelect)
            }
            val items = state.items
            when {
                state.isLoading && items == null -> LoadingView()
                state.error != null -> ErrorView(message = state.error, onRetry = onRetry)
                items != null && items.isEmpty() -> EmptyView(
                    title = stringResource(listedLevel.emptyListTitleRes),
                    icon = listedLevel.icon,
                    message = stringResource(R.string.archive_empty_message),
                )
                else -> {
                    SearchField(
                        text = state.searchText,
                        placeholder = stringResource(listedLevel.searchPromptRes),
                        onTextChange = onSearchTextChange,
                    )
                    PullToRefreshBox(
                        isRefreshing = state.isLoading && items != null,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.isFiltering && state.visibleItems.isEmpty()) {
                            EmptyView(
                                title = stringResource(R.string.archive_search_no_results, state.searchQuery),
                                icon = Icons.Filled.SearchOff,
                                modifier = Modifier.testTag("state.noResults"),
                            )
                        } else {
                            ArchiveList(
                                title = stringResource(listedLevel.listTitleRes),
                                items = state.visibleItems,
                                // Free exams come from the full list, so a search never changes them.
                                lockedPaths = lockState.lockedPaths(items),
                                onItemClick = onItemClick,
                                onLockedExamTap = onLockedExamTap,
                                header = listHeader.takeUnless { state.isFiltering },
                                onDelete = onDeleteExam,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The toolbar bell: opens the inbox and shows how many announcements are unread (iOS bell + badge). */
@Composable
private fun InboxBell(onClick: () -> Unit, viewModel: InboxBadgeViewModel = hiltViewModel()) {
    val unread by viewModel.unreadCount.collectAsStateWithLifecycle()
    val description = if (unread > 0) {
        pluralStringResource(R.plurals.cd_inbox_open_unread, unread, unread)
    } else {
        stringResource(R.string.cd_inbox_open)
    }
    IconButton(onClick = onClick, modifier = Modifier.testTag("toolbar.inbox")) {
        BadgedBox(badge = { if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) } }) {
            Icon(imageVector = Icons.Filled.Notifications, contentDescription = description)
        }
    }
}

@Composable
private fun SearchField(text: String, placeholder: String, onTextChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onTextChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.cd_clear_search))
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("browse.search"),
    )
}

@Composable
private fun ArchiveList(
    title: String,
    items: List<ArchiveItem>,
    lockedPaths: Set<ArchivePath>,
    onItemClick: (ArchiveItem) -> Unit,
    onLockedExamTap: (ArchiveItem) -> Unit,
    header: (@Composable () -> Unit)? = null,
    onDelete: ((ArchiveItem) -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("browse.list"),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (header != null) item(key = "homeHeader") { header() }
        item(key = "sectionHeader") {
            SectionHeader(title = title, count = items.size)
        }
        items(items = items, key = { it.path.components.joinToString("/") }) { item ->
            val isLocked = item.path in lockedPaths
            val row = @Composable {
                ArchiveRow(
                    item = item,
                    isLocked = isLocked,
                    // A locked exam never opens directly; it offers the unlock sheet (iOS ArchiveNavigationRow).
                    onClick = { if (isLocked) onLockedExamTap(item) else onItemClick(item) },
                )
            }
            if (onDelete != null) {
                DeletableRow(onDelete = { onDelete(item) }, testTag = "browse.delete.${item.name}", content = row)
            } else {
                row()
            }
            HorizontalDivider(Modifier.padding(start = 72.dp))
        }
    }
}

/**
 * A row an admin can swipe from the end to delete (iOS trailing swipe action, no full swipe): the swipe only
 * asks for confirmation and springs back. TalkBack users get the same action as a custom accessibility action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeletableRow(onDelete: () -> Unit, testTag: String, content: @Composable () -> Unit) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) currentOnDelete()
            false
        },
    )
    val actionLabel = stringResource(R.string.moderation_delete_exam_action)
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
                Text(text = actionLabel, color = MaterialTheme.colorScheme.onError)
            }
        },
        modifier = Modifier
            .semantics {
                customActions = listOf(CustomAccessibilityAction(actionLabel) { currentOnDelete(); true })
            }
            .testTag(testTag),
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/** "Sınav silinsin mi?": the deletion cannot be undone. */
@Composable
private fun DeleteExamDialog(examName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.moderation_delete_exam_dialog_title)) },
        text = { Text(stringResource(R.string.moderation_delete_exam_dialog_message, examName)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("browse.delete.confirm")) {
                Text(stringResource(R.string.moderation_delete_exam_action), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("browse.delete.cancel")) {
                Text(stringResource(R.string.moderation_dialog_cancel))
            }
        },
    )
}

@Composable
private fun DeleteFailedDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.moderation_delete_exam_failed_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("browse.delete.failed.ok")) {
                Text(stringResource(R.string.common_ok))
            }
        },
    )
}

/** Level title and the number of listed entries (iOS ArchiveSectionHeader). */
@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) { heading() }
            .testTag("browse.sectionHeader"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One entry: level icon, name and child count (or the exam's content); an exam also shows its format, or a lock
 * with the unlock cost when [isLocked].
 */
@Composable
private fun ArchiveRow(item: ArchiveItem, isLocked: Boolean, onClick: () -> Unit) {
    val exam = item.exam
    // An exam node without files cannot be opened (iOS shows it disabled).
    val enabled = item.level != ArchiveLevel.EXAM || exam != null
    val subtitle = when {
        exam != null -> exam.kind.contentText(exam.fileCount)
        item.childCount != null -> item.level.childCountText(item.childCount)
        item.level == ArchiveLevel.EXAM -> stringResource(R.string.exam_no_files)
        else -> null
    }
    val lockedHint = stringResource(R.string.exam_locked_hint)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClickLabel = if (isLocked) lockedHint else null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("row.${item.name}"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon = exam?.kind?.icon ?: item.level.icon,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (exam != null) {
            if (isLocked) {
                LockBadge(cost = ExamLock.UNLOCK_COST, modifier = Modifier.testTag("row.lock"))
            } else {
                ExamKindBadge(text = stringResource(exam.kind.badgeRes))
            }
        }
    }
}

@ViFiThemePreviews
@Composable
private fun BrowseContentLoadedPreview() {
    val university = ArchivePath.ROOT.appending("itu", "İSTANBUL TEKNİK ÜNİVERSİTESİ")
    val lesson = university.appending("muh", "MÜHENDİSLİK").appending("blg", "BİLGİSAYAR").appending("blg101", "BLG 101")
    ViFiTheme {
        BrowseContent(
            state = BrowseUiState(
                path = lesson,
                isLoading = false,
                items = listOf(
                    ArchiveItem(lesson.appending("v", "2023 Vize"), ArchiveLevel.EXAM, null, ExamSummary(ExamKind.PDF, 1)),
                    ArchiveItem(lesson.appending("f", "2022 Final"), ArchiveLevel.EXAM, null, ExamSummary(ExamKind.IMAGES, 3)),
                ),
            ),
            onSearchTextChange = {}, onItemClick = {}, onRetry = {}, onRefresh = {}, onBack = {},
            onBreadcrumbSelect = {}, onHomeClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BrowseContentErrorPreview() {
    ViFiTheme {
        BrowseContent(
            state = BrowseUiState(isLoading = false, error = ArchiveError.Offline.userMessage),
            onSearchTextChange = {}, onItemClick = {}, onRetry = {}, onRefresh = {}, onBack = null,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BrowseContentLoadingPreview() {
    ViFiTheme {
        Box { BrowseContent(BrowseUiState(), {}, {}, {}, {}, null) }
    }
}
