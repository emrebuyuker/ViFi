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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.model.AdminSubject
import com.salticusteam.vifi.core.model.SubjectNameSimilarity

/** What the admin picked in the subject picker (iOS `SubjectPick`). */
sealed interface SubjectPick {
    data class Existing(val subject: AdminSubject) : SubjectPick
    data class New(val name: String) : SubjectPick
}

/** The picker's search rules (iOS `SubjectPickerSheet`), kept pure for tests. */
object SubjectPickerLogic {

    /** The subjects whose name contains the typed text (case- and diacritic-insensitive); all for a blank query. */
    fun matches(subjects: List<AdminSubject>, query: String): List<AdminSubject> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return subjects
        val folded = SubjectNameSimilarity.fold(trimmed)
        return subjects.filter { SubjectNameSimilarity.fold(it.name).contains(folded) }
    }

    /** The typed name when no subject already has it (by comparable key); `null` for a blank query. */
    fun newName(subjects: List<AdminSubject>, query: String): String? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val key = SubjectNameSimilarity.key(trimmed)
        return trimmed.takeUnless { subjects.any { SubjectNameSimilarity.key(it.name) == key } }
    }
}

/**
 * Searchable list of subjects to move a course to or merge a subject into (iOS `SubjectPickerSheet`). With
 * [allowsNew], a typed name that matches no subject offers "yeni ders oluştur".
 */
@Composable
fun SubjectPickerDialog(
    title: String,
    subjects: List<AdminSubject>,
    allowsNew: Boolean,
    onPick: (SubjectPick) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        SubjectPickerContent(title, subjects, allowsNew, onPick, onDismiss)
    }
}

/** Stateless picker content; previews and UI tests drive this. */
@Composable
fun SubjectPickerContent(
    title: String,
    subjects: List<AdminSubject>,
    allowsNew: Boolean,
    onPick: (SubjectPick) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = SubjectPickerLogic.matches(subjects, query)
    val newName = if (allowsNew) SubjectPickerLogic.newName(subjects, query) else null

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ViFiTopAppBar(title = title, onBack = onCancel) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().testTag("subjects.picker")) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.conversion_admin_subjects_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("subjects.picker.search"),
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (newName != null) {
                    item(key = "new") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(SubjectPick.New(newName)) }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("subjects.picker.new"),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.conversion_admin_subjects_picker_new, newName))
                        }
                        HorizontalDivider()
                    }
                }
                items(items = matches, key = { it.id }) { subject ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(SubjectPick.Existing(subject)) }
                            .semantics(mergeDescendants = true) {}
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("subjects.picker.row.${subject.id}"),
                    ) {
                        Text(subject.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = subjectCounts(subject),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** "2 ders · 57 soru" (iOS `"\(courseCount) ders · \(questionCount) soru"`). */
@Composable
internal fun subjectCounts(subject: AdminSubject): String =
    stringResource(
        R.string.conversion_admin_subjects_counts,
        pluralStringResource(R.plurals.conversion_admin_subjects_course_count, subject.courseCount, subject.courseCount),
        pluralStringResource(R.plurals.conversion_admin_subjects_question_count, subject.questionCount, subject.questionCount),
    )
