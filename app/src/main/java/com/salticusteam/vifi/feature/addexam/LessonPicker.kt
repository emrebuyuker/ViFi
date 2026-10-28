package com.salticusteam.vifi.feature.addexam

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.CatalogSelection

/**
 * Picks the lesson (`ders`) of a submission (iOS LessonPickerView): the archive lessons of the chosen
 * university's matching department, plus "Farklı ders ekle" for a lesson the archive does not have yet.
 * Shown over the add-exam form; choosing a value or going back returns to it.
 */
@Composable
fun LessonPicker(
    university: String,
    department: String,
    selection: CatalogSelection,
    onSelect: (CatalogSelection) -> Unit,
    onBack: () -> Unit,
    // Keyed by the placement, so a changed university/department loads afresh while a reopen reuses the list.
    viewModel: LessonPickerViewModel = hiltViewModel<LessonPickerViewModel, LessonPickerViewModel.Factory>(
        key = "lessonPicker|$university|$department",
    ) { factory -> factory.create(university, department) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LessonPickerContent(
        state = state,
        selection = selection,
        onQueryChange = viewModel::onQueryChange,
        onRetry = viewModel::onRetry,
        onSelect = onSelect,
        onBack = onBack,
    )
}

@Composable
internal fun LessonPickerContent(
    state: LessonPickerUiState,
    selection: CatalogSelection,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onSelect: (CatalogSelection) -> Unit,
    onBack: () -> Unit,
) {
    var isAddingCustom by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ViFiTopAppBar(title = stringResource(R.string.add_exam_lesson), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .testTag("lessonPicker.list"),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.add_exam_lesson_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("lessonPicker.search"),
                )
            }

            item(key = "header") {
                Text(
                    text = stringResource(R.string.add_exam_lesson_existing),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { heading() },
                )
            }

            when {
                state.isLoading -> item(key = "loading") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("lessonPicker.loading"),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.add_exam_lesson_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.error != null -> item(key = "error") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("lessonPicker.error"),
                    ) {
                        Text(state.error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onRetry, modifier = Modifier.testTag("lessonPicker.retry")) {
                            Text(stringResource(R.string.common_try_again))
                        }
                    }
                }
                state.matches.isEmpty() -> item(key = "empty") {
                    Text(
                        text = stringResource(
                            if (state.query.isBlank()) R.string.add_exam_lesson_empty else R.string.add_exam_lesson_no_match,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("lessonPicker.empty"),
                    )
                }
                else -> items(state.matches, key = { it.path.components.joinToString("/") }) { lesson ->
                    val isSelected = !selection.isEmpty && !selection.isCustom && selection.name == lesson.name
                    ListItem(
                        headlineContent = { Text(lesson.name) },
                        trailingContent = if (isSelected) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null, // announced through the selected state
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            null
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier
                            .clickable {
                                onSelect(CatalogSelection.archive(lesson.name))
                            }
                            .semantics { selected = isSelected }
                            .testTag("lessonPicker.option"),
                    )
                }
            }

            item(key = "custom") {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.add_exam_lesson_add_custom)) },
                    supportingContent = { Text(stringResource(R.string.add_exam_lesson_custom_footer)) },
                    leadingContent = {
                        Icon(
                            Icons.Filled.AddCircleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .clickable { isAddingCustom = true }
                        .testTag("lessonPicker.addCustom"),
                )
            }
        }
    }

    if (isAddingCustom) {
        CustomLessonDialog(
            initialName = state.query.trim(),
            onConfirm = { name ->
                val custom = CatalogSelection.custom(name)
                if (!custom.isEmpty) {
                    isAddingCustom = false
                    onSelect(custom)
                }
            },
            onDismiss = { isAddingCustom = false },
        )
    }
}

/** Asks for the name of a lesson the archive does not list (iOS "Ders Ekle" alert). */
@Composable
private fun CustomLessonDialog(initialName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_exam_lesson_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.add_exam_lesson_dialog_message))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.add_exam_lesson_dialog_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    modifier = Modifier.testTag("lessonPicker.customField"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("lessonPicker.customConfirm"),
            ) {
                Text(stringResource(R.string.add_exam_lesson_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.add_exam_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun LessonPickerPreview() {
    val department = ArchivePath.ROOT.appending("itu", "İTÜ").appending("muh", "Mühendislik").appending("blg", "Bilgisayar")
    ViFiTheme {
        LessonPickerContent(
            state = LessonPickerUiState(
                isLoading = false,
                lessons = listOf(ArchiveItem(department.appending("blg101", "BLG 101"), ArchiveLevel.LESSON, 4, null)),
            ),
            selection = CatalogSelection.EMPTY,
            onQueryChange = {},
            onRetry = {},
            onSelect = {},
            onBack = {},
        )
    }
}
