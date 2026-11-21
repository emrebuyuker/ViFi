package com.salticusteam.vifi.feature.conversionadmin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.model.AdminSubject

/**
 * The admin's subject list (P5; iOS `SubjectMappingView`): every country-wide course identity with its course and
 * question counts, a search, and an "Olası eşleşme" hint on single-university subjects whose name closely matches
 * another subject. The same [viewModel] backs the detail destination (the host shares it through this entry).
 */
@Composable
fun SubjectMappingScreen(
    onOpenSubject: (subjectId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: SubjectMappingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SubjectMappingContent(
        state = state,
        onOpenSubject = onOpenSubject,
        onSearchChange = viewModel::onSearchChange,
        onRetry = viewModel::refresh,
        onRefresh = viewModel::refresh,
        onBack = onBack,
    )
}

/** Stateless subject list content; previews and UI tests drive this. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectMappingContent(
    state: SubjectMappingUiState,
    onOpenSubject: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ViFiTopAppBar(title = stringResource(R.string.conversion_admin_subjects_title), onBack = onBack) },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        val subjects = state.subjects
        when {
            subjects == null && state.loadError != null -> ErrorView(state.loadError, onRetry, contentModifier)
            subjects == null -> LoadingView(contentModifier, title = stringResource(R.string.conversion_admin_subjects_loading))
            subjects.isEmpty() -> EmptyView(
                title = stringResource(R.string.conversion_admin_subjects_empty_title),
                icon = Icons.Filled.MenuBook,
                message = stringResource(R.string.conversion_admin_subjects_empty_message),
                modifier = contentModifier.testTag("subjects.empty"),
            )
            else -> PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = contentModifier) {
                val filtered = state.filteredSubjects
                LazyColumn(modifier = Modifier.fillMaxSize().testTag("subjects.list")) {
                    item(key = "search") {
                        OutlinedTextField(
                            value = state.searchText,
                            onValueChange = onSearchChange,
                            label = { Text(stringResource(R.string.conversion_admin_subjects_search)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("subjects.search"),
                        )
                    }
                    state.actionError?.let { message ->
                        item(key = "error") { ActionError(message) }
                    }
                    items(items = filtered, key = { it.id }) { subject ->
                        SubjectRow(
                            subject = subject,
                            possibleMatch = state.possibleMatch(subject.id),
                            modifier = Modifier.clickable { onOpenSubject(subject.id) }.testTag("subjects.row.${subject.id}"),
                        )
                        HorizontalDivider()
                    }
                    item(key = "footer") {
                        Text(
                            text = pluralStringResource(R.plurals.conversion_admin_subjects_footer, filtered.size, filtered.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("subjects.footer"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActionError(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("subjects.error"),
        )
    }
}

/** One subject: its name, how many courses and questions it holds, and the "Olası eşleşme" hint (iOS `SubjectRow`). */
@Composable
private fun SubjectRow(subject: AdminSubject, possibleMatch: AdminSubject?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(subject.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = subjectCounts(subject),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (possibleMatch != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CallMerge, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text(
                    text = stringResource(R.string.conversion_admin_subjects_possible_match, possibleMatch.name),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.testTag("subjects.possibleMatch"),
                )
            }
        }
    }
}
