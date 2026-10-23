package com.salticusteam.vifi.feature.addexam

import com.salticusteam.vifi.core.model.ExamSubmission

/** The pure assembly of the form's validated fields into an [ExamSubmission] (iOS `builtSubmission`). */
object AddExamValidation {

    private val spaces = " +".toRegex()

    /** The submission built from [state], or `null` while a field is missing or anything is invalid. */
    fun build(state: AddExamUiState): ExamSubmission? {
        if (state.ownerId.isEmpty() || state.files.isEmpty()) return null
        val submission = ExamSubmission(
            university = validName(state.university.name) ?: return null,
            faculty = validName(state.faculty.name) ?: return null,
            department = validName(state.department.name) ?: return null,
            lesson = validName(state.lesson.name) ?: return null,
            examName = validName(state.examName.name) ?: return null,
            year = state.year,
            kind = state.kind,
            files = state.files,
        )
        return submission.takeIf { it.isValid }
    }

    /** A trimmed, space-collapsed field value, or `null` when empty or longer than the text cap. */
    fun validName(input: String): String? {
        val normalized = input.trim().replace(spaces, " ")
        return normalized.takeIf(ExamSubmission::isValidText)
    }
}
