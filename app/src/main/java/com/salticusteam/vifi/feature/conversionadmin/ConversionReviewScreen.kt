package com.salticusteam.vifi.feature.conversionadmin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import com.salticusteam.vifi.core.model.ExamQuestion

/** What the review screen's controls do; a bundle so the stateless content stays previewable and testable. */
class ConversionReviewActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onShowOriginal: () -> Unit = {},
    /** Saves an edited draft; returns an error message, or `null` on success. */
    val onSaveQuestion: suspend (ExamQuestion) -> String? = { null },
    val onRegenerateQuestion: (order: Int, provider: AIProvider) -> Unit = { _, _ -> },
    val onRegenerateAll: (AIProvider) -> Unit = {},
    val onPublish: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onTitleYearChange: (String) -> Unit = {},
    val onTitleEntryChange: (String?) -> Unit = {},
    val onTitleNumberChange: (String) -> Unit = {},
    val onSubjectModeChange: (SubjectMode) -> Unit = {},
    val onSubjectIdChange: (String) -> Unit = {},
    val onNewSubjectNameChange: (String) -> Unit = {},
)

/**
 * The second approval of one ready job (iOS `ConversionReviewView`): the draft questions with per-question edit and
 * regenerate (K8), the exam's list title (K28), the course mapping, "Yayınla", "Tümünü yeniden üret" (K14) and
 * "Sınavı iptal et". The original pages open in their own destination ([onShowOriginal]); [onFinished] leaves the
 * review once its outcome was acknowledged.
 */
@Composable
fun ConversionReviewScreen(
    jobId: String,
    onShowOriginal: () -> Unit,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    viewModel: ConversionReviewViewModel = hiltViewModel<ConversionReviewViewModel, ConversionReviewViewModel.Factory>(
        key = jobId,
    ) { it.create(jobId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The automatic refresh after a single-question regeneration ends with the screen (iOS `.onDisappear`).
    DisposableEffect(viewModel) { onDispose { viewModel.stopPolling() } }

    ConversionReviewContent(
        state = state,
        actions = ConversionReviewActions(
            onBack = onBack,
            onRetry = viewModel::refresh,
            onShowOriginal = onShowOriginal,
            onSaveQuestion = { question ->
                if (viewModel.save(question)) null
                else (viewModel.uiState.value.error as? ReviewError.Server)?.message ?: GENERIC_ERROR_MESSAGE
            },
            onRegenerateQuestion = viewModel::regenerateQuestion,
            onRegenerateAll = viewModel::regenerateAll,
            onPublish = viewModel::publish,
            onDiscard = viewModel::discard,
            onTitleYearChange = viewModel::onTitleYearChange,
            onTitleEntryChange = viewModel::onTitleEntryChange,
            onTitleNumberChange = viewModel::onTitleNumberChange,
            onSubjectModeChange = viewModel::onSubjectModeChange,
            onSubjectIdChange = viewModel::onSubjectIdChange,
            onNewSubjectNameChange = viewModel::onNewSubjectNameChange,
        ),
        onFinished = onFinished,
    )
}

/** Stateless review content; previews and UI tests drive this. */
@Composable
fun ConversionReviewContent(
    state: ConversionReviewUiState,
    actions: ConversionReviewActions,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingOrder by rememberSaveable { mutableStateOf<Int?>(null) }
    var regeneratingOrder by rememberSaveable { mutableStateOf<Int?>(null) }
    var isPickingProviderForAll by rememberSaveable { mutableStateOf(false) }
    var isConfirmingPublish by rememberSaveable { mutableStateOf(false) }
    var isConfirmingDiscard by rememberSaveable { mutableStateOf(false) }

    // An action running ends only on its own: leaving mid-publish would lose its result (iOS locks the controls).
    BackHandler(enabled = state.isWorking) {}

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViFiTopAppBar(
                title = state.job?.examTitle ?: stringResource(R.string.conversion_admin_review_title),
                onBack = { if (!state.isWorking) actions.onBack() },
                actions = {
                    IconButton(onClick = actions.onRetry, modifier = Modifier.testTag("conversion.review.refresh")) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.conversion_admin_review_refresh))
                    }
                },
            )
        },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        val job = state.job
        val drafts = state.drafts
        when {
            state.loadError != null && drafts == null -> ErrorView(state.loadError, actions.onRetry, contentModifier)
            job == null || drafts == null ->
                LoadingView(contentModifier, title = stringResource(R.string.conversion_admin_review_loading))
            else -> LazyColumn(modifier = contentModifier.testTag("conversion.review")) {
                item(key = "info") {
                    ReviewInfoSection(job, state.needsReviewCount, actions.onShowOriginal)
                }
                item(key = "title") {
                    ConversionTitleSection(
                        state = state,
                        onYearChange = actions.onTitleYearChange,
                        onEntryChange = actions.onTitleEntryChange,
                        onNumberChange = actions.onTitleNumberChange,
                    )
                }
                state.error?.let { error ->
                    item(key = "error") { ReviewMessage(text = error.text(), isError = true, tag = "conversion.review.error") }
                }
                state.info?.let { info ->
                    item(key = "info-message") { ReviewMessage(text = info.text(), isError = false, tag = "conversion.review.infoMessage") }
                }
                item(key = "questions-header") {
                    ReviewSectionHeader(stringResource(R.string.conversion_admin_review_questions, drafts.size))
                }
                items(items = drafts, key = { it.order }) { question ->
                    DraftQuestionRow(
                        question = question,
                        isBusy = state.isWorking,
                        isRegenerating = question.order in state.regeneratingOrders,
                        onEdit = { editingOrder = question.order },
                        onRegenerate = { regeneratingOrder = question.order },
                    )
                }
                item(key = "subject") {
                    ReviewSubjectSection(
                        state = state,
                        onModeChange = actions.onSubjectModeChange,
                        onSubjectIdChange = actions.onSubjectIdChange,
                        onNewSubjectNameChange = actions.onNewSubjectNameChange,
                    )
                }
                item(key = "actions") {
                    ReviewActionsSection(
                        isWorking = state.isWorking,
                        canPublish = state.canPublish,
                        onPublish = { isConfirmingPublish = true },
                        onRegenerateAll = { isPickingProviderForAll = true },
                        onDiscard = { isConfirmingDiscard = true },
                    )
                }
            }
        }

        state.drafts?.firstOrNull { it.order == editingOrder }?.let { question ->
            QuestionEditorDialog(
                question = question,
                onSave = actions.onSaveQuestion,
                onDismiss = { editingOrder = null },
            )
        }
    }

    regeneratingOrder?.let { order ->
        AiProviderDialog(
            title = stringResource(R.string.conversion_admin_review_regenerate_question_provider),
            onPick = {
                regeneratingOrder = null
                actions.onRegenerateQuestion(order, it)
            },
            onDismiss = { regeneratingOrder = null },
        )
    }
    if (isPickingProviderForAll) {
        AiProviderDialog(
            title = stringResource(R.string.conversion_admin_review_regenerate_all_provider),
            onPick = {
                isPickingProviderForAll = false
                actions.onRegenerateAll(it)
            },
            onDismiss = { isPickingProviderForAll = false },
        )
    }
    if (isConfirmingPublish) {
        AlertDialog(
            onDismissRequest = { isConfirmingPublish = false },
            title = { Text(stringResource(R.string.conversion_admin_publish_confirm_title)) },
            text = { Text(stringResource(R.string.conversion_admin_publish_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        isConfirmingPublish = false
                        actions.onPublish()
                    },
                    modifier = Modifier.testTag("conversion.review.publish.confirm"),
                ) { Text(stringResource(R.string.conversion_admin_review_publish)) }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmingPublish = false }) { Text(stringResource(R.string.conversion_admin_cancel)) }
            },
        )
    }
    if (isConfirmingDiscard) {
        AlertDialog(
            onDismissRequest = { isConfirmingDiscard = false },
            title = { Text(stringResource(R.string.conversion_admin_discard_confirm_title)) },
            text = { Text(stringResource(R.string.conversion_admin_discard_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        isConfirmingDiscard = false
                        actions.onDiscard()
                    },
                    modifier = Modifier.testTag("conversion.review.discard.confirm"),
                ) {
                    Text(stringResource(R.string.conversion_admin_review_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmingDiscard = false }) { Text(stringResource(R.string.conversion_admin_cancel)) }
            },
        )
    }
    state.outcome?.let { outcome ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(outcome.title()) },
            text = { Text(outcome.message(), modifier = Modifier.testTag("conversion.review.outcome.message")) },
            confirmButton = {
                TextButton(onClick = onFinished, modifier = Modifier.testTag("conversion.review.outcome.ok")) {
                    Text(stringResource(R.string.conversion_admin_ok))
                }
            },
        )
    }
}

@Composable
private fun ReviewMessage(text: String, isError: Boolean, tag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.Info,
            contentDescription = null,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun ReviewError.text(): String = when (this) {
    is ReviewError.Server -> message
    ReviewError.SubjectIdMissing -> stringResource(R.string.conversion_admin_error_subject_id)
    ReviewError.NewSubjectNameMissing -> stringResource(R.string.conversion_admin_error_new_subject)
}

@Composable
private fun ReviewInfo.text(): String = when (this) {
    is ReviewInfo.RegeneratingQuestion -> stringResource(R.string.conversion_admin_info_regenerating_question, order)
}

@Composable
private fun ReviewOutcome.title(): String = when (this) {
    is ReviewOutcome.Published -> stringResource(R.string.conversion_admin_outcome_published_title)
    ReviewOutcome.Discarded -> stringResource(R.string.conversion_admin_outcome_discarded_title)
    ReviewOutcome.Regenerating -> stringResource(R.string.conversion_admin_outcome_regenerating_title)
}

@Composable
private fun ReviewOutcome.message(): String = when (this) {
    is ReviewOutcome.Published -> stringResource(R.string.conversion_admin_outcome_published_message, questionCount)
    ReviewOutcome.Discarded -> stringResource(R.string.conversion_admin_outcome_discarded_message)
    ReviewOutcome.Regenerating -> stringResource(R.string.conversion_admin_outcome_regenerating_message)
}
