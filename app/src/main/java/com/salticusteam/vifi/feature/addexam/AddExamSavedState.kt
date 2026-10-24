package com.salticusteam.vifi.feature.addexam

import com.salticusteam.vifi.core.model.CatalogSelection
import com.salticusteam.vifi.core.model.ExamKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the "Sınav Ekle" form keeps in its `SavedStateHandle`, so the picks survive process death (rotation keeps
 * the ViewModel anyway). The pages are too large for saved state: they live in the draft directory named by
 * [draftId] and are read back from there. [submissionId] is set while a submit is queued or uploading, so a
 * restored form follows that upload instead of offering to send again.
 */
@Serializable
internal data class AddExamSavedForm(
    val draftId: String,
    val university: CatalogSelection,
    val faculty: CatalogSelection,
    val department: CatalogSelection,
    val lesson: CatalogSelection,
    val examName: CatalogSelection,
    val year: Int,
    /** `ExamKind.raw`. */
    val kind: String,
    /** The open picker's `AddExamPicker.name`, `null` while the form itself is shown. */
    val picker: String? = null,
    val submissionId: String? = null,
) {
    /** [state] (a fresh form) with the saved picks; a value the form no longer offers is left as it was. */
    fun applyTo(state: AddExamUiState): AddExamUiState = state.copy(
        university = university,
        faculty = faculty,
        department = department,
        lesson = lesson,
        examName = examName,
        year = year.takeIf { it in state.yearOptions } ?: state.year,
        kind = ExamKind.fromRaw(kind) ?: state.kind,
        picker = picker?.let { name -> AddExamPicker.entries.firstOrNull { it.name == name } },
    )

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun of(state: AddExamUiState, draftId: String, submissionId: String?) = AddExamSavedForm(
            draftId = draftId,
            university = state.university,
            faculty = state.faculty,
            department = state.department,
            lesson = state.lesson,
            examName = state.examName,
            year = state.year,
            kind = state.kind.raw,
            picker = state.picker?.name,
            submissionId = submissionId,
        )

        /** The saved form, or `null` when there is none or it cannot be read (e.g. written by another version). */
        fun decode(encoded: String?): AddExamSavedForm? = encoded?.let {
            try {
                json.decodeFromString(serializer(), it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
