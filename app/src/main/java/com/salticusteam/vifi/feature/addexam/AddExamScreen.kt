package com.salticusteam.vifi.feature.addexam

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.formatCoins
import com.salticusteam.vifi.core.designsystem.component.labelResId
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.CatalogSelection
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSubmission
import com.salticusteam.vifi.core.model.SubmissionFile
import com.salticusteam.vifi.core.model.SubmissionTier
import com.salticusteam.vifi.core.model.UserProfile
import com.salticusteam.vifi.feature.profile.CatalogPicker

/**
 * The "Sınav Ekle" flow (iOS AddExamView): pick the school (pre-filled from the profile, list-only), the lesson
 * (archive or typed), the exam name / year / kind, attach image pages or a PDF, and submit it for moderation.
 * Opened from the home screen and from the unlock sheet's "Sınav Ekle"; [onClose] returns to where it came from,
 * also after a successful submit. Back and close are disabled while the submit is being queued; once it uploads in
 * the background the form may be closed (the upload goes on and its outcome is notified).
 */
@Composable
fun AddExamScreen(
    ownerId: String,
    profile: UserProfile?,
    onClose: () -> Unit,
    viewModel: AddExamViewModel = hiltViewModel<AddExamViewModel, AddExamViewModel.Factory> { factory ->
        factory.create(ownerId, profile)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onScreenShown()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AddExamEvent.Submitted -> onClose()
            }
        }
    }

    // Photo Picker and the document picker need no storage permission; results are content URIs.
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = ExamSubmission.MAX_FILE_COUNT),
    ) { uris -> viewModel.onImagesPicked(uris.map { it.toString() }) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onPdfPicked(it.toString()) }
    }

    val picker = state.picker
    // While a picker is open, back closes it; while a submit is being queued, back does nothing.
    BackHandler(enabled = picker != null || !state.canClose) {
        if (picker != null) viewModel.onPickerDismiss()
    }

    when (picker) {
        null -> AddExamContent(
            state = state,
            onClose = onClose,
            onSubmit = viewModel::onSubmit,
            onPickerOpen = viewModel::onPickerOpen,
            onYearSelected = viewModel::onYearSelected,
            onKindSelected = viewModel::onKindSelected,
            onPickImages = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onPickPdf = { pdfPicker.launch(arrayOf(PDF_MIME_TYPE)) },
            onClearFiles = viewModel::onClearFiles,
        )
        AddExamPicker.LESSON -> LessonPicker(
            university = state.university.name,
            department = state.department.name,
            selection = state.lesson,
            onSelect = viewModel::onSelection,
            onBack = viewModel::onPickerDismiss,
        )
        else -> CatalogPicker(
            title = picker.label(),
            options = state.options(picker),
            selection = state.selection(picker),
            testTagPrefix = picker.testTag,
            onSelect = viewModel::onSelection,
            onBack = viewModel::onPickerDismiss,
            allowsCustom = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddExamContent(
    state: AddExamUiState,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
    onPickerOpen: (AddExamPicker) -> Unit,
    onYearSelected: (Int) -> Unit,
    onKindSelected: (ExamKind) -> Unit,
    onPickImages: () -> Unit,
    onPickPdf: () -> Unit,
    onClearFiles: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_exam_title), style = MaterialTheme.typography.titleLarge) },
                // A modal flow: close ("Vazgeç") rather than a back arrow.
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        enabled = state.canClose,
                        modifier = Modifier.testTag("addExam.cancel"),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.add_exam_cancel))
                    }
                },
                actions = {
                    if (state.isWorking) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .size(24.dp)
                                .testTag("addExam.progress"),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(
                            onClick = onSubmit,
                            enabled = state.canSubmit,
                            modifier = Modifier.testTag("addExam.submit"),
                        ) {
                            Text(stringResource(R.string.add_exam_submit), fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Pinned above the form (not a list item), so it shows whatever the form is scrolled to.
            if (state.isSendingInBackground) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.add_exam_background_hint)) },
                    leadingContent = {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.testTag("addExam.backgroundHint"),
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("addExam.form"),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "school") {
                    Section(title = stringResource(R.string.add_exam_section_school)) {
                        PickerRow(AddExamPicker.UNIVERSITY, state.university, state.isEditable, onPickerOpen)
                        PickerRow(AddExamPicker.FACULTY, state.faculty, state.isEditable, onPickerOpen)
                        PickerRow(AddExamPicker.DEPARTMENT, state.department, state.isEditable, onPickerOpen)
                    }
                }
                item(key = "lesson") {
                    Section(title = stringResource(R.string.add_exam_section_lesson)) {
                        PickerRow(AddExamPicker.LESSON, state.lesson, state.canPickLesson, onPickerOpen)
                    }
                }
                item(key = "exam") {
                    Section(title = stringResource(R.string.add_exam_section_exam)) {
                        PickerRow(AddExamPicker.EXAM_NAME, state.examName, state.isEditable, onPickerOpen)
                        YearPicker(state.year, state.yearOptions, state.isEditable, onYearSelected)
                        KindSelector(state.kind, state.isEditable, onKindSelected)
                    }
                }
                item(key = "files") {
                    Section(title = stringResource(R.string.add_exam_section_files)) {
                        FilesSection(state, onPickImages, onPickPdf, onClearFiles)
                    }
                }
                item(key = "coinNote") {
                    CoinNoteRow(state.coinNote)
                }
                state.error?.let { message ->
                    item(key = "error") {
                        ListItem(
                            headlineContent = { Text(message, color = MaterialTheme.colorScheme.error) },
                            leadingContent = {
                                Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                            modifier = Modifier.testTag("addExam.error"),
                        )
                    }
                }
            }
        }
    }
}

/** The row label and picker title for [AddExamPicker]. */
@Composable
private fun AddExamPicker.label(): String = stringResource(
    when (this) {
        AddExamPicker.UNIVERSITY -> R.string.add_exam_university
        AddExamPicker.FACULTY -> R.string.add_exam_faculty
        AddExamPicker.DEPARTMENT -> R.string.add_exam_department
        AddExamPicker.LESSON -> R.string.add_exam_lesson
        AddExamPicker.EXAM_NAME -> R.string.add_exam_exam_name
    },
)

/** The stable test tag of [AddExamPicker]'s row, also the prefix of its picker (iOS accessibility identifiers). */
private val AddExamPicker.testTag: String
    get() = when (this) {
        AddExamPicker.UNIVERSITY -> "addExam.university"
        AddExamPicker.FACULTY -> "addExam.faculty"
        AddExamPicker.DEPARTMENT -> "addExam.department"
        AddExamPicker.LESSON -> "addExam.lesson"
        AddExamPicker.EXAM_NAME -> "addExam.examName"
    }

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            // Secondary text like iOS section headers; the brand orange is too light for small text.
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics { heading() },
        )
        content()
    }
}

/** A row showing the current value; tapping it opens [picker]'s list. */
@Composable
private fun PickerRow(
    picker: AddExamPicker,
    selection: CatalogSelection,
    enabled: Boolean,
    onPickerOpen: (AddExamPicker) -> Unit,
) {
    ListItem(
        headlineContent = { Text(picker.label()) },
        supportingContent = {
            Text(
                text = selection.name.ifEmpty { stringResource(R.string.add_exam_select) },
                color = if (selection.isEmpty) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onPickerOpen(picker) }
            .testTag(picker.testTag),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearPicker(year: Int, options: List<Int>, enabled: Boolean, onYearSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("addExam.year"),
    ) {
        OutlinedTextField(
            value = year.toString(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.add_exam_year)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.toString()) },
                    onClick = {
                        onYearSelected(option)
                        expanded = false
                    },
                    modifier = Modifier.testTag("addExam.year.$option"),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindSelector(kind: ExamKind, enabled: Boolean, onKindSelected: (ExamKind) -> Unit) {
    val kinds = ExamKind.entries
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.add_exam_kind),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("addExam.kind"),
        ) {
            kinds.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == kind,
                    onClick = { onKindSelected(option) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = kinds.size),
                    modifier = Modifier.testTag("addExam.kind.${option.raw}"),
                ) {
                    Text(stringResource(option.labelResId()))
                }
            }
        }
    }
}

@Composable
private fun FilesSection(
    state: AddExamUiState,
    onPickImages: () -> Unit,
    onPickPdf: () -> Unit,
    onClearFiles: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val pickEnabled = state.isEditable && !state.isPreparingFiles
        when (state.kind) {
            ExamKind.IMAGES -> OutlinedButton(
                onClick = onPickImages,
                enabled = pickEnabled,
                modifier = Modifier.fillMaxWidth().testTag("addExam.pickImages"),
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.add_exam_pick_images))
            }
            ExamKind.PDF -> OutlinedButton(
                onClick = onPickPdf,
                enabled = pickEnabled,
                modifier = Modifier.fillMaxWidth().testTag("addExam.pickPDF"),
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(
                    stringResource(if (state.files.isEmpty()) R.string.add_exam_pick_pdf else R.string.add_exam_replace_pdf),
                )
            }
        }

        if (state.isPreparingFiles) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("addExam.preparing"),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.add_exam_preparing_files),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (state.files.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = when (state.kind) {
                        ExamKind.IMAGES -> pluralStringResource(
                            R.plurals.add_exam_pages_selected,
                            state.files.size,
                            state.files.size,
                        )
                        ExamKind.PDF -> stringResource(R.string.add_exam_pdf_selected)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("addExam.fileSummary"),
                )
                TextButton(
                    onClick = onClearFiles,
                    enabled = state.isEditable,
                    modifier = Modifier.testTag("addExam.clearFiles"),
                ) {
                    Text(stringResource(R.string.add_exam_clear_files), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Text(
            text = when (state.kind) {
                ExamKind.IMAGES -> stringResource(R.string.add_exam_files_footer_images, ExamSubmission.MAX_FILE_COUNT)
                ExamKind.PDF -> stringResource(R.string.add_exam_files_footer_pdf)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CoinNoteRow(note: CoinNote) {
    val text = when (note) {
        is CoinNote.Estimated -> stringResource(
            R.string.add_exam_coin_note_tier,
            formatCoins(note.coins),
            stringResource(note.tier.labelResId()),
        )
        is CoinNote.Flat -> stringResource(R.string.add_exam_coin_note_flat, formatCoins(note.coins))
        CoinNote.Generic -> stringResource(R.string.add_exam_coin_note_generic)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("addExam.coinNote"),
    ) {
        Icon(
            imageVector = Icons.Filled.MonetizationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

/** The Turkish label of [ExamKind] in the kind selector. */
private fun ExamKind.labelResId(): Int = when (this) {
    ExamKind.IMAGES -> R.string.add_exam_kind_images
    ExamKind.PDF -> R.string.add_exam_kind_pdf
}

private const val PDF_MIME_TYPE = "application/pdf"

@Preview(showBackground = true)
@Composable
private fun AddExamContentPreview() {
    ViFiTheme {
        AddExamContent(
            state = AddExamUiState(
                ownerId = "preview",
                university = CatalogSelection.stored("Boğaziçi Üniversitesi", "bogazici-universitesi", false),
                faculty = CatalogSelection.stored("Mühendislik Fakültesi", "muhendislik-fakultesi", false),
                department = CatalogSelection.stored("Bilgisayar Mühendisliği", "bilgisayar-muhendisligi", false),
                year = 2026,
                yearOptions = (2000..2026).reversed().toList(),
                files = listOf(SubmissionFile(byteArrayOf(1), order = 1), SubmissionFile(byteArrayOf(2), order = 2)),
                estimatedTier = SubmissionTier.NEW_COURSE,
            ),
            onClose = {},
            onSubmit = {},
            onPickerOpen = {},
            onYearSelected = {},
            onKindSelected = {},
            onPickImages = {},
            onPickPdf = {},
            onClearFiles = {},
        )
    }
}
