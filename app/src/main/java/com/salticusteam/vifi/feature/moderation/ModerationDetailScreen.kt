package com.salticusteam.vifi.feature.moderation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.repository.fake.FakeModerationService
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.component.labelResId
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.ApprovalResult
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSubmissionRecord

/**
 * One submission under review (iOS `ModerationDetailView`): its place in the archive, a link to preview the
 * files, and the approve / reject actions. While an action runs, every control and the back navigation are
 * locked (iOS `interactiveDismissDisabled`).
 */
@Composable
fun ModerationDetailScreen(
    submissionId: String,
    onPreviewFiles: () -> Unit,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    viewModel: ModerationDetailViewModel = hiltViewModel<ModerationDetailViewModel, ModerationDetailViewModel.Factory>(
        key = submissionId,
    ) { it.create(submissionId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ModerationDetailEvent.Finished -> onFinished()
            }
        }
    }

    ModerationDetailContent(
        state = state,
        onPreviewFiles = onPreviewFiles,
        onApprove = viewModel::approve,
        onRejectRequested = viewModel::onRejectRequested,
        onRejectReasonChange = viewModel::onRejectReasonChange,
        onConfirmReject = viewModel::confirmReject,
        onRejectDismissed = viewModel::onRejectDismissed,
        onApprovalAcknowledged = viewModel::onApprovalAcknowledged,
        onBack = onBack,
    )
}

/** Stateless detail content; previews and UI tests drive this. */
@Composable
fun ModerationDetailContent(
    state: ModerationDetailUiState,
    onPreviewFiles: () -> Unit,
    onApprove: () -> Unit,
    onRejectRequested: () -> Unit,
    onRejectReasonChange: (String) -> Unit,
    onConfirmReject: () -> Unit,
    onRejectDismissed: () -> Unit,
    onApprovalAcknowledged: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.isWorking) {}

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViFiTopAppBar(
                title = stringResource(R.string.moderation_detail_title),
                onBack = { if (!state.isWorking) onBack() },
            )
        },
    ) { padding ->
        val record = state.record
        if (state.isLoading) {
            LoadingView(Modifier.padding(padding))
        } else if (record == null) {
            EmptyView(
                title = stringResource(R.string.moderation_detail_missing_title),
                icon = Icons.Filled.SearchOff,
                message = stringResource(R.string.moderation_detail_missing_message),
                modifier = Modifier.padding(padding).testTag("moderation.detail.missing"),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .testTag("moderation.detail"),
            ) {
                DetailRows(record)
                HorizontalDivider()
                PreviewRow(record = record, enabled = !state.isWorking, onClick = onPreviewFiles)
                HorizontalDivider()
                Actions(isWorking = state.isWorking, onApprove = onApprove, onReject = onRejectRequested)
                state.error?.let { DetailError(it) }
            }
        }
    }

    if (state.isConfirmingReject) {
        RejectDialog(
            reason = state.rejectReason,
            onReasonChange = onRejectReasonChange,
            onConfirm = onConfirmReject,
            onDismiss = onRejectDismissed,
        )
    }
    state.approval?.let { result ->
        ApprovedDialog(result = result, onAcknowledge = onApprovalAcknowledged)
    }
}

@Composable
private fun DetailRows(record: ExamSubmissionRecord) {
    DetailRow(R.string.moderation_detail_university, record.university)
    DetailRow(R.string.moderation_detail_faculty, record.faculty)
    DetailRow(R.string.moderation_detail_department, record.department)
    DetailRow(R.string.moderation_detail_lesson, record.lesson)
    DetailRow(R.string.moderation_detail_exam, record.exam)
    DetailRow(R.string.moderation_detail_kind, stringResource(record.kind.moderationLabelRes))
    record.createdAt?.let { DetailRow(R.string.moderation_detail_date, it.toSubmissionDate()) }
}

/** A label with its value on the right (iOS `LabeledContent`). */
@Composable
private fun DetailRow(label: Int, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

/** "Dosyaları incele (N görsel/PDF)": opens the file preview. */
@Composable
private fun PreviewRow(record: ExamSubmissionRecord, enabled: Boolean, onClick: () -> Unit) {
    val count = record.files.size
    val text = when (record.kind) {
        ExamKind.IMAGES -> pluralStringResource(R.plurals.moderation_detail_preview_images, count, count)
        ExamKind.PDF -> pluralStringResource(R.plurals.moderation_detail_preview_pdf, count, count)
    }
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(Icons.Filled.Visibility, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            headlineColor = MaterialTheme.colorScheme.primary,
            leadingIconColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag("moderation.detail.preview"),
    )
}

/** "Onayla ve Yayınla" (with a spinner while working), "Reddet", and the footer. */
@Composable
private fun Actions(isWorking: Boolean, onApprove: () -> Unit, onReject: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onApprove,
            enabled = !isWorking,
            modifier = Modifier.fillMaxWidth().testTag("moderation.detail.approve"),
        ) {
            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp).testTag("moderation.detail.progress"),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.moderation_approve_button))
        }
        OutlinedButton(
            onClick = onReject,
            enabled = !isWorking,
            modifier = Modifier.fillMaxWidth().testTag("moderation.detail.reject"),
        ) {
            Text(stringResource(R.string.moderation_reject_button), color = MaterialTheme.colorScheme.error)
        }
        Text(
            text = stringResource(R.string.moderation_actions_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailError(message: String) {
    ListItem(
        headlineContent = { Text(message) },
        leadingContent = { Icon(Icons.Filled.Error, contentDescription = null) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            headlineColor = MaterialTheme.colorScheme.error,
            leadingIconColor = MaterialTheme.colorScheme.error,
        ),
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .testTag("moderation.detail.error"),
    )
}

/** "Reddedilsin mi?" with an optional reason (iOS alert with a text field). */
@Composable
private fun RejectDialog(
    reason: String,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.moderation_reject_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.moderation_reject_dialog_message))
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text(stringResource(R.string.moderation_reject_reason_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("moderation.reject.reason"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("moderation.reject.confirm")) {
                Text(stringResource(R.string.moderation_reject_button), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("moderation.reject.cancel")) {
                Text(stringResource(R.string.moderation_dialog_cancel))
            }
        },
    )
}

/** "Onaylandı" with the tier and coins credited; only "Tamam" closes it (then the detail closes too). */
@Composable
private fun ApprovedDialog(result: ApprovalResult, onAcknowledge: () -> Unit) {
    val message = if (result.coins > 0) {
        stringResource(R.string.moderation_approved_with_coins, stringResource(result.tier.labelResId()), result.coins)
    } else {
        stringResource(R.string.moderation_approved_plain)
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.moderation_approved_title)) },
        text = { Text(message, modifier = Modifier.testTag("moderation.approved.message")) },
        confirmButton = {
            TextButton(onClick = onAcknowledge, modifier = Modifier.testTag("moderation.approved.ok")) {
                Text(stringResource(R.string.common_ok))
            }
        },
    )
}

@ViFiThemePreviews
@Composable
private fun ModerationDetailPreview() {
    ViFiTheme {
        ModerationDetailContent(
            state = ModerationDetailUiState(record = FakeModerationService.SAMPLES.first()),
            onPreviewFiles = {},
            onApprove = {},
            onRejectRequested = {},
            onRejectReasonChange = {},
            onConfirmReject = {},
            onRejectDismissed = {},
            onApprovalAcknowledged = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModerationDetailWorkingPreview() {
    ViFiTheme {
        ModerationDetailContent(
            state = ModerationDetailUiState(
                record = FakeModerationService.SAMPLES.last(),
                isWorking = true,
                error = "Bu gönderim artık beklemede değil; listeyi yenile.",
            ),
            onPreviewFiles = {},
            onApprove = {},
            onRejectRequested = {},
            onRejectReasonChange = {},
            onConfirmReject = {},
            onRejectDismissed = {},
            onApprovalAcknowledged = {},
            onBack = {},
        )
    }
}
