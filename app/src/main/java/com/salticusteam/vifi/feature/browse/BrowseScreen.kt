package com.salticusteam.vifi.feature.browse

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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.component.childCountText
import com.salticusteam.vifi.core.designsystem.component.contentText
import com.salticusteam.vifi.core.designsystem.component.emptyListTitleRes
import com.salticusteam.vifi.core.designsystem.component.icon
import com.salticusteam.vifi.core.designsystem.component.listTitleRes
import com.salticusteam.vifi.core.designsystem.component.searchPromptRes
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSummary

/**
 * An archive list: universities on the home screen (root path) and every drill-down level (iOS HomeView /
 * BrowseView, simplified — breadcrumbs, recents and the unlock sheet arrive with their roadmap items).
 */
@Composable
fun BrowseScreen(
    onItemClick: (ArchiveItem) -> Unit,
    onBack: (() -> Unit)?,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onScreenShown() }

    BrowseContent(
        state = state,
        onSearchTextChange = viewModel::onSearchTextChange,
        onItemClick = onItemClick,
        onRetry = viewModel::load,
        onRefresh = viewModel::refresh,
        onBack = onBack,
    )
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
) {
    val listedLevel = state.path.childLevel ?: ArchiveLevel.EXAM
    val title = state.path.displayName ?: stringResource(R.string.archive_home_title)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ViFiTopAppBar(title = title, onBack = onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                                onItemClick = onItemClick,
                            )
                        }
                    }
                }
            }
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
private fun ArchiveList(title: String, items: List<ArchiveItem>, onItemClick: (ArchiveItem) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("browse.list"),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "sectionHeader") {
            SectionHeader(title = title, count = items.size)
        }
        items(items = items, key = { it.path.components.joinToString("/") }) { item ->
            ArchiveRow(item = item, onClick = { onItemClick(item) })
            HorizontalDivider(Modifier.padding(start = 72.dp))
        }
    }
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

/** One entry: level icon, name and child count (or the exam's content). */
@Composable
private fun ArchiveRow(item: ArchiveItem, onClick: () -> Unit) {
    val exam = item.exam
    // An exam node without files cannot be opened (iOS shows it disabled).
    val enabled = item.level != ArchiveLevel.EXAM || exam != null
    val subtitle = when {
        exam != null -> exam.kind.contentText(exam.fileCount)
        item.childCount != null -> item.level.childCountText(item.childCount)
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("row.${item.name}"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = exam?.kind?.icon ?: item.level.icon)
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
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
    }
}

@Preview(showBackground = true)
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
