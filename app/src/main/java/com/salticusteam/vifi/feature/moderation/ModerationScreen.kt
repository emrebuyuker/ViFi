package com.salticusteam.vifi.feature.moderation

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
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.repository.fake.FakeModerationService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubmitterProfileReader
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.component.icon
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.ExamSubmissionRecord

/**
 * The admin moderation queue (iOS `ModerationView`), opened from the account screen by an admin only: the
 * submissions awaiting review, each opening its detail where it is previewed and approved or rejected.
 */
@Composable
fun ModerationScreen(
    onRecordClick: (submissionId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: ModerationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onScreenShown() }

    ModerationContent(
        state = state,
        onRecordClick = onRecordClick,
        onRowShown = viewModel::onRowShown,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onBack = onBack,
    )
}

/** Stateless queue content; previews and UI tests drive this. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationContent(
    state: ModerationUiState,
    onRecordClick: (String) -> Unit,
    /** A row became visible: its owner's submitter lookup starts (once per uid). */
    onRowShown: (ownerId: String) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ViFiTopAppBar(title = stringResource(R.string.moderation_title), onBack = onBack) },
    ) { padding ->
        val records = state.records
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        when {
            records == null && state.error != null -> ErrorView(state.error, onRetry, contentModifier)
            records == null -> LoadingView(contentModifier, title = stringResource(R.string.moderation_loading))
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = contentModifier,
            ) {
                if (records.isEmpty()) {
                    EmptyView(
                        title = stringResource(R.string.moderation_empty_title),
                        icon = Icons.Filled.Inbox,
                        message = stringResource(R.string.moderation_empty_message),
                        modifier = Modifier.testTag("moderation.empty"),
                    )
                } else {
                    RecordList(
                        records = records,
                        submitters = state.submitters,
                        onRecordClick = onRecordClick,
                        onRowShown = onRowShown,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordList(
    records: List<ExamSubmissionRecord>,
    submitters: Map<String, SubmitterState>,
    onRecordClick: (String) -> Unit,
    onRowShown: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("moderation.list")) {
        items(items = records, key = { it.id }) { record ->
            // Lazily, per visible row (iOS `.task { loadSubmitter }`); the directory reads a uid only once.
            LaunchedEffect(record.ownerId) { onRowShown(record.ownerId) }
            ModerationRow(
                record = record,
                submitter = submitters[record.ownerId],
                onClick = { onRecordClick(record.id) },
            )
            HorizontalDivider(Modifier.padding(start = 72.dp))
        }
        item(key = "footer") {
            Text(
                text = pluralStringResource(R.plurals.moderation_queue_footer, records.size, records.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("moderation.footer"),
            )
        }
    }
}

/** One row of the queue: the exam, its place in the archive, its submitter and its size (iOS `ModerationRow`). */
@Composable
private fun ModerationRow(record: ExamSubmissionRecord, submitter: SubmitterState?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("moderation.row.${record.id}"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = record.kind.icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = record.exam, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.moderation_row_location, record.lesson, record.department),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = record.university,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SubmitterLines(submitter)
        }
        Text(
            text = fileCountText(record),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Gönderen: Ad Soyad" plus "Üniversite · Bölüm", or a quiet placeholder / non-blocking note (iOS `submitterLines`). */
@Composable
private fun SubmitterLines(submitter: SubmitterState?) {
    when (submitter) {
        is SubmitterState.Loaded -> Column(Modifier.testTag("moderation.submitter")) {
            Text(
                text = stringResource(R.string.moderation_row_submitter, submitter.info.fullName),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val affiliation = submitter.info.affiliation()
            if (affiliation.isNotEmpty()) {
                Text(
                    text = affiliation,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SubmitterState.Missing -> SubmitterRowNote(
            text = stringResource(R.string.moderation_submitter_missing),
            modifier = Modifier.testTag("moderation.submitter"),
        )
        SubmitterState.Failed -> SubmitterRowNote(
            text = stringResource(R.string.moderation_submitter_failed),
            modifier = Modifier.testTag("moderation.submitter"),
        )
        SubmitterState.Loading, null -> SubmitterRowNote(text = stringResource(R.string.moderation_submitter_loading))
    }
}

@Composable
private fun SubmitterRowNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@ViFiThemePreviews
@Composable
private fun ModerationContentPreview() {
    ViFiTheme {
        ModerationContent(
            state = ModerationUiState(
                isLoading = false,
                records = FakeModerationService.SAMPLES,
                submitters = mapOf(
                    FakeSubmitterProfileReader.SAMPLE_UID to SubmitterState.Loaded(FakeSubmitterProfileReader.SAMPLE),
                ),
            ),
            onRecordClick = {},
            onRowShown = {},
            onRetry = {},
            onRefresh = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModerationEmptyPreview() {
    ViFiTheme {
        ModerationContent(
            state = ModerationUiState(isLoading = false, records = emptyList()),
            onRecordClick = {},
            onRowShown = {},
            onRetry = {},
            onRefresh = {},
            onBack = {},
        )
    }
}
