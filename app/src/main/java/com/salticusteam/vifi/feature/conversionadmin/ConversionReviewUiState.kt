package com.salticusteam.vifi.feature.conversionadmin

import androidx.annotation.StringRes
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.ExamConversion
import com.salticusteam.vifi.core.model.ExamNameCatalog
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.util.ExamListTitle

/** How the admin maps a published exam onto a country-wide course identity (`subjects/<id>`; iOS `SubjectMode`). */
enum class SubjectMode {
    /** The server's own suggestion (nothing is sent). */
    SUGGESTED,

    /** An existing subject id the admin typed. */
    EXISTING,

    /** A new subject, by name. */
    NEW,
}

/** What ended the review (iOS `ReviewOutcome`). */
sealed interface ReviewOutcome {
    data class Published(val questionCount: Int) : ReviewOutcome
    data object Discarded : ReviewOutcome

    /** The whole exam was sent back to the AI; the job is `queued` again. */
    data object Regenerating : ReviewOutcome
}

/** The last failed action, shown in the form: the server's / data layer's Turkish message, or a local input check. */
sealed interface ReviewError {
    data class Server(val message: String) : ReviewError

    /** "Mevcut kimlik" is picked but no subject id was typed. */
    data object SubjectIdMissing : ReviewError

    /** "Yeni ders" is picked but no name was typed. */
    data object NewSubjectNameMissing : ReviewError
}

/** A hint after an action started (iOS `infoMessage`). */
sealed interface ReviewInfo {
    /** A single-question regeneration started; the draft changes once the server finishes. */
    data class RegeneratingQuestion(val order: Int) : ReviewInfo
}

/** Why the exam's list title cannot be published yet (iOS `titleProblem`); the screen shows its text. */
enum class TitleProblem(@get:StringRes val messageRes: Int) {
    PICK_NAME(R.string.conversion_admin_title_problem_name),
    YEAR(R.string.conversion_admin_title_problem_year),
    NUMBER_RANGE(R.string.conversion_admin_title_problem_number),
    NUMBER_ON_NUMBERED_NAME(R.string.conversion_admin_title_problem_numbered),
}

/**
 * The second-approval review of one ready job (iOS `ConversionReviewViewModel` state): the draft questions, the exam's
 * list title (K28) and the course mapping being edited, and what the last action did.
 */
data class ConversionReviewUiState(
    /** `null` while the job is being found again (the process was recreated). */
    val job: ExamConversion? = null,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    /** `null` until the first successful load. */
    val drafts: List<ExamQuestion>? = null,
    /** The drafts whose single-question regeneration is running (server lease not expired at the last read). */
    val regeneratingOrders: Set<Int> = emptySet(),
    val isWorking: Boolean = false,
    val outcome: ReviewOutcome? = null,
    val error: ReviewError? = null,
    val info: ReviewInfo? = null,
    val subjectMode: SubjectMode = SubjectMode.SUGGESTED,
    val subjectId: String = "",
    val newSubjectName: String = "",
    /** `[<year> ]<list name>[ <1–99>]` (K28): pre-filled from the server's suggestion; empty when there is none. */
    val titleYear: String = "",
    val titleEntryId: String? = null,
    val titleNumber: String = "",
) {

    /** Draft questions the model was unsure about and nobody edited yet. */
    val needsReviewCount: Int get() = drafts?.count { it.needsReview } ?: 0

    /** Why the title cannot be published yet, or `null` when it can. */
    val titleProblem: TitleProblem?
        get() {
            val entryId = titleEntryId
            if (entryId == null || ExamNameCatalog.entry(entryId) == null) return TitleProblem.PICK_NAME
            val year = titleYear.trim()
            if (year.isNotEmpty() && ExamListTitle.year(year) == null) return TitleProblem.YEAR
            val number = titleNumber.trim()
            if (number.isNotEmpty()) {
                if (ExamListTitle.sequenceNumber(number) == null) return TitleProblem.NUMBER_RANGE
                if (entryId.startsWith("kisa-sinav-")) return TitleProblem.NUMBER_ON_NUMBERED_NAME
            }
            return null
        }

    /** The list title the editor's parts make, normalised as the server stores it; `null` while they are not valid. */
    val examTitle: String?
        get() {
            val entryId = titleEntryId
            if (titleProblem != null || entryId == null) return null
            val parts = ExamListTitle(
                year = ExamListTitle.year(titleYear.trim()),
                entryId = entryId,
                number = ExamListTitle.sequenceNumber(titleNumber.trim()),
            )
            return ExamListTitle.parse(parts.title)?.title
        }

    val canPublish: Boolean
        get() = !isWorking && outcome == null && drafts?.isNotEmpty() == true && examTitle != null
}
