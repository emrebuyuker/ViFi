package com.salticusteam.vifi.feature.conversionadmin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.model.AdminSubject
import com.salticusteam.vifi.core.model.SubjectCourse
import com.salticusteam.vifi.core.model.SubjectTarget
import java.text.Collator
import java.util.Locale

/** What the detail screen's fixes do; a bundle so the stateless content stays previewable and testable. */
class SubjectDetailActions(
    val onBack: () -> Unit = {},
    val onSetCourseSubject: (SubjectCourse, SubjectTarget) -> Unit = { _, _ -> },
    val onRename: (AdminSubject, String) -> Unit = { _, _ -> },
    val onMerge: (AdminSubject, AdminSubject) -> Unit = { _, _ -> },
)

/**
 * One subject's courses, grouped by university, with the three fixes (iOS `SubjectDetailView`): change a course's
 * subject, rename the subject, merge it into another. The screen reads the subject from the shared [viewModel], so it
 * follows every reload and closes itself when the subject is gone (merged).
 */
@Composable
fun SubjectDetailScreen(
    subjectId: String,
    onBack: () -> Unit,
    viewModel: SubjectMappingViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SubjectDetailContent(
        subjectId = subjectId,
        state = state,
        actions = SubjectDetailActions(
            onBack = onBack,
            onSetCourseSubject = viewModel::setCourseSubject,
            onRename = viewModel::rename,
            onMerge = viewModel::merge,
        ),
    )
}

/** Stateless subject detail content; previews and UI tests drive this. */
@Composable
fun SubjectDetailContent(
    subjectId: String,
    state: SubjectMappingUiState,
    actions: SubjectDetailActions,
    modifier: Modifier = Modifier,
) {
    val subject = state.subject(subjectId)
    val isGone = state.subjects != null && subject == null

    // The subject was merged away (or the list no longer has it): leave, as iOS does when the subject is gone.
    LaunchedEffect(isGone) { if (isGone) actions.onBack() }

    var isMenuOpen by remember { mutableStateOf(false) }
    var isPickingMerge by rememberSaveable { mutableStateOf(false) }
    var isRenaming by rememberSaveable { mutableStateOf(false) }
    // The course whose "Değiştir" asked for a new subject, and the move waiting for its confirmation.
    var changingCourseId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingMove by remember { mutableStateOf<PendingMove?>(null) }
    var mergeTarget by remember { mutableStateOf<AdminSubject?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViFiTopAppBar(
                title = subject?.name.orEmpty(),
                onBack = { if (!state.isWorking) actions.onBack() },
                actions = {
                    if (subject != null) {
                        Box {
                            IconButton(
                                onClick = { isMenuOpen = true },
                                modifier = Modifier.testTag("subjects.menu"),
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.conversion_admin_subject_menu))
                            }
                            DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.conversion_admin_subject_rename)) },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = {
                                        isMenuOpen = false
                                        isRenaming = true
                                    },
                                    modifier = Modifier.testTag("subjects.rename"),
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.conversion_admin_subject_merge)) },
                                    leadingIcon = { Icon(Icons.Filled.CallMerge, contentDescription = null) },
                                    onClick = {
                                        isMenuOpen = false
                                        isPickingMerge = true
                                    },
                                    modifier = Modifier.testTag("subjects.merge"),
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        if (subject == null) {
            // Loading while the shared list is read again; once loaded without it the screen is already leaving.
            LoadingView(contentModifier)
        } else {
            Box(contentModifier) {
                SubjectBody(subject, state, enabled = !state.isWorking, onChange = { changingCourseId = it.id })
                if (state.isWorking) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    if (subject == null) return

    val otherSubjects = state.subjects.orEmpty().filter { it.id != subject.id }
    val changing = subject.courses.firstOrNull { it.id == changingCourseId }
    if (changing != null) {
        SubjectPickerDialog(
            title = stringResource(R.string.conversion_admin_subject_pick_new),
            subjects = otherSubjects,
            allowsNew = true,
            onPick = { pick ->
                changingCourseId = null
                pendingMove = PendingMove(changing, pick)
            },
            onDismiss = { changingCourseId = null },
        )
    }
    pendingMove?.let { move ->
        AlertDialog(
            onDismissRequest = { pendingMove = null },
            title = { Text(stringResource(R.string.conversion_admin_move_title)) },
            text = {
                Text(stringResource(R.string.conversion_admin_move_message, move.course.name, move.targetName, move.course.questionCount))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingMove = null
                        actions.onSetCourseSubject(move.course, move.target)
                    },
                    modifier = Modifier.testTag("subjects.move.confirm"),
                ) { Text(stringResource(R.string.conversion_admin_move_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingMove = null }) { Text(stringResource(R.string.conversion_admin_cancel)) }
            },
        )
    }
    if (isPickingMerge) {
        SubjectPickerDialog(
            title = stringResource(R.string.conversion_admin_subject_pick_merge),
            subjects = otherSubjects,
            allowsNew = false,
            onPick = { pick ->
                isPickingMerge = false
                if (pick is SubjectPick.Existing) mergeTarget = pick.subject
            },
            onDismiss = { isPickingMerge = false },
        )
    }
    mergeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { mergeTarget = null },
            title = { Text(stringResource(R.string.conversion_admin_merge_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.conversion_admin_merge_message,
                        subject.name,
                        target.name,
                        subject.courseCount,
                        subject.questionCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mergeTarget = null
                        actions.onMerge(subject, target)
                    },
                    modifier = Modifier.testTag("subjects.merge.confirm"),
                ) { Text(stringResource(R.string.conversion_admin_merge_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { mergeTarget = null }) { Text(stringResource(R.string.conversion_admin_cancel)) }
            },
        )
    }
    if (isRenaming) {
        var newName by rememberSaveable(subject.id, subject.name) { mutableStateOf(subject.name) }
        AlertDialog(
            onDismissRequest = { isRenaming = false },
            title = { Text(stringResource(R.string.conversion_admin_rename_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.conversion_admin_rename_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("subjects.rename.field"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isRenaming = false
                        actions.onRename(subject, newName)
                    },
                    modifier = Modifier.testTag("subjects.rename.confirm"),
                ) { Text(stringResource(R.string.conversion_admin_edit_save)) }
            },
            dismissButton = {
                TextButton(onClick = { isRenaming = false }) { Text(stringResource(R.string.conversion_admin_cancel)) }
            },
        )
    }
}

/** A course move waiting for the admin's confirmation (owner decision, 14 Nov 2026). */
private class PendingMove(val course: SubjectCourse, val pick: SubjectPick) {
    val target: SubjectTarget
        get() = when (pick) {
            is SubjectPick.Existing -> SubjectTarget.Existing(pick.subject.id)
            is SubjectPick.New -> SubjectTarget.New(pick.name)
        }

    val targetName: String
        get() = when (pick) {
            is SubjectPick.Existing -> pick.subject.name
            is SubjectPick.New -> pick.name
        }
}

@Composable
private fun SubjectBody(
    subject: AdminSubject,
    state: SubjectMappingUiState,
    enabled: Boolean,
    onChange: (SubjectCourse) -> Unit,
) {
    val groups = remember(subject) { universityGroups(subject.courses) }
    val possibleMatch = state.possibleMatch(subject.id)
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("subjects.detail")) {
        state.actionError?.let { message ->
            item(key = "error") { ActionError(message) }
        }
        item(key = "summary") {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledCount(
                    stringResource(R.string.conversion_admin_subject_courses),
                    subject.courseCount.toString(),
                )
                LabeledCount(
                    stringResource(R.string.conversion_admin_subject_questions),
                    subject.questionCount.toString(),
                )
                if (possibleMatch != null) {
                    Text(
                        text = stringResource(R.string.conversion_admin_subjects_possible_match, possibleMatch.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.testTag("subjects.possibleMatch"),
                    )
                }
                Text(
                    text = stringResource(R.string.conversion_admin_subject_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
        for (group in groups) {
            item(key = "university-${group.universityId}") {
                Text(
                    text = group.universityName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
                )
            }
            items(items = group.courses, key = { it.id }) { course ->
                CourseRow(course, enabled, onChange = { onChange(course) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LabeledCount(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CourseRow(course: SubjectCourse, enabled: Boolean, onChange: () -> Unit) {
    val changeDescription = stringResource(R.string.conversion_admin_subject_change_course_description, course.name)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(course.name, style = MaterialTheme.typography.bodyLarge)
            if (course.place.isNotEmpty()) {
                Text(
                    text = course.place,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Text(
                text = pluralStringResource(R.plurals.conversion_admin_subjects_question_count, course.questionCount, course.questionCount),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(
            onClick = onChange,
            enabled = enabled,
            modifier = Modifier
                .semantics { contentDescription = changeDescription }
                .testTag("subjects.course.change.${course.id}"),
        ) { Text(stringResource(R.string.conversion_admin_subject_change_course)) }
    }
}

/** The courses of one university, universities and courses by name (iOS `SubjectDetailView.groups`). */
internal data class CourseGroup(val universityId: String, val universityName: String, val courses: List<SubjectCourse>)

internal fun universityGroups(courses: List<SubjectCourse>): List<CourseGroup> {
    val collator = Collator.getInstance(Locale.forLanguageTag("tr-TR"))
    return courses.groupBy { it.universityId }
        .map { (id, group) ->
            CourseGroup(
                universityId = id,
                universityName = group.first().universityName,
                courses = group.sortedWith { lhs, rhs -> collator.compare(lhs.name, rhs.name) },
            )
        }
        .sortedWith { lhs, rhs -> collator.compare(lhs.universityName, rhs.universityName) }
}
