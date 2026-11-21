package com.salticusteam.vifi.feature.conversionadmin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.math.QuestionRichText
import com.salticusteam.vifi.core.model.ExamConversion
import com.salticusteam.vifi.core.model.ExamNameCatalog
import com.salticusteam.vifi.core.model.ExamQuestion
import kotlin.math.roundToInt

/** A section's small heading in the review form (iOS `Section` header). */
@Composable
internal fun ReviewSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
internal fun ReviewFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** Where the exam belongs, model and cost, the review hint and the entry to the original pages (iOS `infoSection`). */
@Composable
internal fun ReviewInfoSection(
    job: ExamConversion,
    needsReviewCount: Int,
    onShowOriginal: () -> Unit,
) {
    val providerAndCost = job.providerAndCost()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (job.placementPath.isNotEmpty()) {
            Text(job.placementPath, style = MaterialTheme.typography.bodyMedium)
        }
        if (providerAndCost.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.conversion_admin_review_model_cost), style = MaterialTheme.typography.bodyMedium)
                Text(providerAndCost, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (needsReviewCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text(
                    text = stringResource(R.string.conversion_admin_review_needs_review, needsReviewCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.testTag("conversion.review.needsReview"),
                )
            }
        }
        if (job.files.isNotEmpty()) {
            OutlinedButton(onClick = onShowOriginal, modifier = Modifier.testTag("conversion.review.original")) {
                Icon(Icons.Filled.Visibility, contentDescription = null)
                Text(stringResource(R.string.conversion_admin_review_original), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * The exam's title on the second approval (owner decision K28; iOS `ConversionTitleSection`): the original title for
 * reference and the list title it is published with, year (4 digits or none) + one of the exam-name list's names + an
 * optional sequence number. Without a server suggestion the admin has to pick a name before "Yayınla" is enabled.
 */
@Composable
internal fun ConversionTitleSection(
    state: ConversionReviewUiState,
    onYearChange: (String) -> Unit,
    onEntryChange: (String?) -> Unit,
    onNumberChange: (String) -> Unit,
) {
    val job = state.job
    val original = job?.originalTitle ?: job?.placementNames?.lastOrNull()
    var isMenuOpen by remember { mutableStateOf(false) }
    val problem = state.titleProblem
    val title = state.examTitle ?: problem?.let { stringResource(it.messageRes) }.orEmpty()

    ReviewSectionHeader(stringResource(R.string.conversion_admin_title_section))
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (original != null) {
            Row(modifier = Modifier.fillMaxWidth().testTag("conversion.review.title.original"), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.conversion_admin_title_original), style = MaterialTheme.typography.bodyMedium)
                Text(original, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column {
            OutlinedButton(
                onClick = { isMenuOpen = true },
                modifier = Modifier.fillMaxWidth().testTag("conversion.review.title.name"),
            ) {
                Text(
                    text = state.titleEntryId?.let { ExamNameCatalog.entry(it)?.name }
                        ?: stringResource(R.string.conversion_admin_title_pick),
                    modifier = Modifier.weight(1f),
                )
            }
            DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.conversion_admin_title_pick)) },
                    onClick = {
                        isMenuOpen = false
                        onEntryChange(null)
                    },
                )
                for (entry in ExamNameCatalog.entries) {
                    DropdownMenuItem(
                        text = { Text(entry.name) },
                        onClick = {
                            isMenuOpen = false
                            onEntryChange(entry.id)
                        },
                        modifier = Modifier.testTag("conversion.review.title.name.${entry.id}"),
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.titleYear,
            onValueChange = onYearChange,
            label = { Text(stringResource(R.string.conversion_admin_title_year)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("conversion.review.title.year"),
        )
        OutlinedTextField(
            value = state.titleNumber,
            onValueChange = onNumberChange,
            label = { Text(stringResource(R.string.conversion_admin_title_number)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("conversion.review.title.number"),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (state.examTitle == null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { contentDescription = title }.testTag("conversion.review.title"),
        )
    }
    ReviewFooter(stringResource(R.string.conversion_admin_title_footer))
}

/** One draft question: its text, the answer and flags, with edit and regenerate actions (iOS `DraftQuestionRow`). */
@Composable
internal fun DraftQuestionRow(
    question: ExamQuestion,
    isBusy: Boolean,
    isRegenerating: Boolean,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val answerPrefix = question.answer.choice?.let { "$it — " }.orEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("conversion.review.question.${question.order}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.conversion_admin_draft_title, question.label), style = MaterialTheme.typography.titleMedium)
            if (question.needsReview) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.conversion_admin_draft_check),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                question.confidence?.let { confidence ->
                    Text(
                        text = stringResource(R.string.conversion_admin_draft_confidence, (confidence * 100).roundToInt()),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        QuestionRichText(text = question.text, style = MaterialTheme.typography.bodyMedium)
        for (choice in question.choices) {
            QuestionRichText(
                text = stringResource(R.string.conversion_admin_draft_choice, choice.label, choice.text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        QuestionRichText(
            text = stringResource(R.string.conversion_admin_draft_answer, answerPrefix, question.answer.text),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        )
        if (isRegenerating) {
            Row(
                modifier = Modifier.testTag("conversion.review.regenerating.${question.order}"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.conversion_admin_draft_regenerating),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        question.regenerationError?.let { error ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("conversion.review.regenerationError.${question.order}"),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onEdit,
                enabled = !isBusy && !isRegenerating,
                modifier = Modifier.testTag("conversion.question.edit.${question.order}"),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.conversion_admin_draft_edit), modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(
                onClick = onRegenerate,
                enabled = !isBusy && !isRegenerating,
                modifier = Modifier.testTag("conversion.question.regenerate.${question.order}"),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.conversion_admin_draft_regenerate), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
    HorizontalDivider()
}

/** The course mapping: the server's suggestion, or an existing id, or a new subject name (iOS `subjectSection`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewSubjectSection(
    state: ConversionReviewUiState,
    onModeChange: (SubjectMode) -> Unit,
    onSubjectIdChange: (String) -> Unit,
    onNewSubjectNameChange: (String) -> Unit,
) {
    val suggested = state.job?.let { it.suggestedSubjectName ?: it.suggestedSubjectId }
    val modes = listOf(
        SubjectMode.SUGGESTED to R.string.conversion_admin_subject_mode_suggested,
        SubjectMode.EXISTING to R.string.conversion_admin_subject_mode_existing,
        SubjectMode.NEW to R.string.conversion_admin_subject_mode_new,
    )
    ReviewSectionHeader(stringResource(R.string.conversion_admin_subject_section))
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (suggested != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.conversion_admin_subject_suggested), style = MaterialTheme.typography.bodyMedium)
                Text(suggested, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().testTag("conversion.review.subjectMode")) {
            modes.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = state.subjectMode == mode,
                    onClick = { onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    modifier = Modifier.testTag("conversion.review.subjectMode.${mode.name.lowercase()}"),
                ) { Text(stringResource(label), maxLines = 1) }
            }
        }
        when (state.subjectMode) {
            SubjectMode.SUGGESTED -> Unit
            SubjectMode.EXISTING -> OutlinedTextField(
                value = state.subjectId,
                onValueChange = onSubjectIdChange,
                label = { Text(stringResource(R.string.conversion_admin_subject_id_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("conversion.review.subjectId"),
            )
            SubjectMode.NEW -> OutlinedTextField(
                value = state.newSubjectName,
                onValueChange = onNewSubjectNameChange,
                label = { Text(stringResource(R.string.conversion_admin_subject_new_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("conversion.review.newSubject"),
            )
        }
    }
    ReviewFooter(stringResource(R.string.conversion_admin_subject_footer))
}

/** "Yayınla", "Tümünü yeniden üret" and "Sınavı iptal et" (iOS `actionsSection`). */
@Composable
internal fun ReviewActionsSection(
    isWorking: Boolean,
    canPublish: Boolean,
    onPublish: () -> Unit,
    onRegenerateAll: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onPublish,
            enabled = canPublish,
            modifier = Modifier.fillMaxWidth().testTag("conversion.review.publish"),
        ) {
            if (isWorking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
            }
            Text(stringResource(R.string.conversion_admin_review_publish), modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(
            onClick = onRegenerateAll,
            enabled = !isWorking,
            modifier = Modifier.fillMaxWidth().testTag("conversion.review.regenerateAll"),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text(stringResource(R.string.conversion_admin_review_regenerate_all), modifier = Modifier.padding(start = 8.dp))
        }
        TextButton(
            onClick = onDiscard,
            enabled = !isWorking,
            modifier = Modifier.fillMaxWidth().testTag("conversion.review.discard"),
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(
                text = stringResource(R.string.conversion_admin_review_discard),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
