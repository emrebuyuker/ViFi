package com.salticusteam.vifi.feature.structuredexam

import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamQuestion

/**
 * The structured exam being read (4.2.0, iOS `StructuredExamViewModel` state). [questions] is `null` until the first
 * successful load and then stays, so a failed reload keeps the cards on screen; [error] is shown only while there is
 * nothing to show. [kind] is the exam's original file kind, kept for the recents entry.
 */
data class StructuredExamUiState(
    val path: ArchivePath,
    val kind: ExamKind,
    val isLoading: Boolean = true,
    val questions: List<ExamQuestion>? = null,
    val error: String? = null,
    /** The `order` of every question whose answer is shown (kept on the device only, never written anywhere). */
    val revealedOrders: Set<Int> = emptySet(),
) {
    /** The exam name, available before the questions load (top bar title). */
    val title: String get() = path.displayName.orEmpty()

    /** "University › Lesson": where the exam comes from (the list header). */
    val subtitle: String
        get() = listOfNotNull(path.displayNames.firstOrNull(), path.displayNames.dropLast(1).lastOrNull())
            .joinToString(" › ")

    /** The exam's full location, "University › … › Exam", prefilled in the "Bize Ulaş" e-mail. */
    val supportPath: String get() = path.displayNames.joinToString(" › ")

    fun isAnswerRevealed(order: Int): Boolean = order in revealedOrders
}
