package com.salticusteam.vifi.feature.conversionadmin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.ExamQuestion
import kotlinx.coroutines.launch

/**
 * A full-screen editor for one question's content: text, speech, choices, answer and solution steps (iOS
 * `QuestionEditorView`). Used for a draft under review and for a published question (K15); [onSave] saves the edited
 * question and returns an error message, or `null` on success (the editor then closes).
 */
@Composable
fun QuestionEditorDialog(
    question: ExamQuestion,
    onSave: suspend (ExamQuestion) -> String?,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(question.id, stateSaver = QuestionEditDraft.Saver) {
        mutableStateOf(QuestionEditDraft(question))
    }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isSaving,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        QuestionEditorContent(
            title = stringResource(R.string.conversion_admin_edit_title, question.label),
            draft = draft,
            isSaving = isSaving,
            error = error,
            onDraftChange = { draft = it },
            onSave = {
                if (!draft.isValid || isSaving) return@QuestionEditorContent
                isSaving = true
                error = null
                val edited = draft.applied(question)
                scope.launch {
                    val message = onSave(edited)
                    isSaving = false
                    if (message == null) onDismiss() else error = message
                }
            },
            onCancel = onDismiss,
        )
    }
}

/** Stateless editor content; previews and UI tests drive this. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionEditorContent(
    title: String,
    draft: QuestionEditDraft,
    isSaving: Boolean,
    error: String?,
    onDraftChange: (QuestionEditDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onCancel, enabled = !isSaving, modifier = Modifier.testTag("question.edit.cancel")) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.conversion_admin_cancel))
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = draft.isValid && !isSaving,
                        modifier = Modifier.testTag("question.edit.save"),
                    ) { Text(stringResource(R.string.conversion_admin_edit_save)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = draft.text,
                onValueChange = { onDraftChange(draft.copy(text = it)) },
                label = { Text(stringResource(R.string.conversion_admin_edit_text)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth().testTag("question.edit.text"),
            )
            OutlinedTextField(
                value = draft.speech,
                onValueChange = { onDraftChange(draft.copy(speech = it)) },
                label = { Text(stringResource(R.string.conversion_admin_edit_speech)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("question.edit.speech"),
            )
            ChoicesSection(draft, onDraftChange)
            AnswerSection(draft, onDraftChange)
            SolutionSection(draft, onDraftChange)
            val message = error ?: draft.problem?.let { stringResource(it.messageRes) }
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("question.edit.error"),
                )
            }
        }
    }
}

@Composable
private fun ChoicesSection(draft: QuestionEditDraft, onDraftChange: (QuestionEditDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.conversion_admin_edit_choices), style = MaterialTheme.typography.titleSmall)
        draft.choices.forEachIndexed { index, choice ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = choice.label,
                    onValueChange = { value -> onDraftChange(draft.withChoice(index, choice.copy(label = value))) },
                    label = { Text(stringResource(R.string.conversion_admin_edit_choice_label_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.width(72.dp).testTag("question.edit.choice.$index.label"),
                )
                OutlinedTextField(
                    value = choice.text,
                    onValueChange = { value -> onDraftChange(draft.withChoice(index, choice.copy(text = value))) },
                    label = { Text(stringResource(R.string.conversion_admin_edit_choice_text)) },
                    modifier = Modifier.weight(1f).testTag("question.edit.choice.$index.text"),
                )
                IconButton(
                    onClick = { onDraftChange(draft.copy(choices = draft.choices.filterIndexed { i, _ -> i != index })) },
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.conversion_admin_edit_choice_remove))
                }
            }
        }
        if (draft.choices.size < QuestionEditDraft.Limit.CHOICES) {
            OutlinedButton(
                onClick = { onDraftChange(draft.copy(choices = draft.choices + QuestionEditDraft.Choice())) },
                modifier = Modifier.testTag("question.edit.choice.add"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.conversion_admin_edit_choice_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun AnswerSection(draft: QuestionEditDraft, onDraftChange: (QuestionEditDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.conversion_admin_edit_answer), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = draft.answerText,
            onValueChange = { onDraftChange(draft.copy(answerText = it)) },
            label = { Text(stringResource(R.string.conversion_admin_edit_answer_text)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("question.edit.answer"),
        )
        if (draft.choices.isNotEmpty()) {
            OutlinedTextField(
                value = draft.answerChoice,
                onValueChange = { onDraftChange(draft.copy(answerChoice = it)) },
                label = { Text(stringResource(R.string.conversion_admin_edit_answer_choice)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth().testTag("question.edit.answerChoice"),
            )
        }
        OutlinedTextField(
            value = draft.answerSpeech,
            onValueChange = { onDraftChange(draft.copy(answerSpeech = it)) },
            label = { Text(stringResource(R.string.conversion_admin_edit_answer_speech)) },
            modifier = Modifier.fillMaxWidth().testTag("question.edit.answerSpeech"),
        )
    }
}

@Composable
private fun SolutionSection(draft: QuestionEditDraft, onDraftChange: (QuestionEditDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.conversion_admin_edit_solution), style = MaterialTheme.typography.titleSmall)
        draft.solution.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = step,
                    onValueChange = { value ->
                        onDraftChange(draft.copy(solution = draft.solution.mapIndexed { i, old -> if (i == index) value else old }))
                    },
                    label = { Text(stringResource(R.string.conversion_admin_edit_step)) },
                    modifier = Modifier.weight(1f).testTag("question.edit.step.$index"),
                )
                IconButton(
                    onClick = { onDraftChange(draft.copy(solution = draft.solution.filterIndexed { i, _ -> i != index })) },
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.conversion_admin_edit_step_remove))
                }
            }
        }
        if (draft.solution.size < QuestionEditDraft.Limit.SOLUTION_STEPS) {
            OutlinedButton(
                onClick = { onDraftChange(draft.copy(solution = draft.solution + "")) },
                modifier = Modifier.testTag("question.edit.step.add"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.conversion_admin_edit_step_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private fun QuestionEditDraft.withChoice(index: Int, choice: QuestionEditDraft.Choice): QuestionEditDraft =
    copy(choices = choices.mapIndexed { i, old -> if (i == index) choice else old })
