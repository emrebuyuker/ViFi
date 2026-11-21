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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.IconBadge
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ExamConversion
import com.salticusteam.vifi.core.model.ExamKind

/**
 * The admin's job list of one kind (iOS `ConversionJobsView`): "Hazır sınavlar" (drafts waiting for the second
 * approval, each opening the review) or "Başarısız işler" (with "Tekrar başlat" and a provider choice).
 */
@Composable
fun ConversionJobsScreen(
    kind: ConversionListKind,
    onOpenJob: (jobId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: ConversionJobsViewModel = hiltViewModel<ConversionJobsViewModel, ConversionJobsViewModel.Factory>(
        key = kind.name,
    ) { it.create(kind) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The admin came back from a review that published, discarded or re-queued a job (iOS `.onAppear`).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshIfLoaded() }

    ConversionJobsContent(
        state = state,
        onOpenJob = onOpenJob,
        onRestart = viewModel::restart,
        onRetry = viewModel::refresh,
        onRefresh = viewModel::refresh,
        onBack = onBack,
    )
}

/** Stateless job list content; previews and UI tests drive this. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversionJobsContent(
    state: ConversionJobsUiState,
    onOpenJob: (String) -> Unit,
    onRestart: (ExamConversion, AIProvider) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The job whose "Tekrar başlat" asked for a provider.
    var restartingId by rememberSaveable { mutableStateOf<String?>(null) }
    val isReady = state.kind == ConversionListKind.READY
    val jobs = state.jobs

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViFiTopAppBar(
                title = stringResource(if (isReady) R.string.conversion_admin_ready_title else R.string.conversion_admin_failed_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        when {
            jobs == null && state.loadError != null -> ErrorView(state.loadError, onRetry, contentModifier)
            jobs == null -> LoadingView(contentModifier, title = stringResource(R.string.conversion_admin_jobs_loading))
            jobs.isEmpty() -> EmptyView(
                title = stringResource(if (isReady) R.string.conversion_admin_ready_empty_title else R.string.conversion_admin_failed_empty_title),
                message = stringResource(if (isReady) R.string.conversion_admin_ready_empty_message else R.string.conversion_admin_failed_empty_message),
                modifier = contentModifier.testTag(if (isReady) "conversion.ready.empty" else "conversion.failed.empty"),
            )
            else -> PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = contentModifier) {
                JobList(
                    jobs = jobs,
                    isReady = isReady,
                    isWorking = state.isWorking,
                    actionError = state.actionError,
                    onOpenJob = onOpenJob,
                    onRestartRequested = { restartingId = it.id },
                )
            }
        }
    }

    val restarting = jobs?.firstOrNull { it.id == restartingId }
    if (restarting != null) {
        AiProviderDialog(
            title = stringResource(R.string.conversion_admin_restart_provider_title),
            onPick = { provider ->
                restartingId = null
                onRestart(restarting, provider)
            },
            onDismiss = { restartingId = null },
        )
    }
}

@Composable
private fun JobList(
    jobs: List<ExamConversion>,
    isReady: Boolean,
    isWorking: Boolean,
    actionError: String?,
    onOpenJob: (String) -> Unit,
    onRestartRequested: (ExamConversion) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(if (isReady) "conversion.ready.list" else "conversion.failed.list"),
    ) {
        if (actionError != null) {
            item(key = "error") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        text = actionError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("conversion.list.error"),
                    )
                }
                HorizontalDivider()
            }
        }
        items(items = jobs, key = { it.id }) { job ->
            if (isReady) {
                JobRow(job, modifier = Modifier.clickable { onOpenJob(job.id) }.testTag("conversion.row.${job.id}"))
            } else {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    JobRow(job)
                    OutlinedButton(
                        onClick = { onRestartRequested(job) },
                        enabled = !isWorking,
                        modifier = Modifier.padding(horizontal = 16.dp).testTag("conversion.restart.${job.id}"),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text(
                            text = stringResource(R.string.conversion_admin_restart),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(start = 72.dp))
        }
    }
}

/** One job: the exam, where it belongs, the provider and cost, and the last error of a failed run (iOS `ConversionJobRow`). */
@Composable
private fun JobRow(job: ExamConversion, modifier: Modifier = Modifier) {
    val providerAndCost = job.providerAndCost()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = if (job.kind == ExamKind.IMAGES) Icons.Filled.PhotoLibrary else Icons.AutoMirrored.Filled.InsertDriveFile)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = job.examTitle, style = MaterialTheme.typography.titleMedium)
            if (job.placementPath.isNotEmpty()) {
                Text(
                    text = job.placementPath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (providerAndCost.isNotEmpty()) {
                Text(
                    text = providerAndCost,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val error = job.error
            if (error != null && job.status == ConversionStatus.FAILED) {
                Text(text = error, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
