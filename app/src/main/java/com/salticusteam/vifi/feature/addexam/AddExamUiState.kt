package com.salticusteam.vifi.feature.addexam

import com.salticusteam.vifi.core.data.catalog.UniversityCatalog
import com.salticusteam.vifi.core.model.CatalogItem
import com.salticusteam.vifi.core.model.CatalogSelection
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamNameCatalog
import com.salticusteam.vifi.core.model.ExamSubmission
import com.salticusteam.vifi.core.model.RewardsConfig
import com.salticusteam.vifi.core.model.SubmissionFile
import com.salticusteam.vifi.core.model.SubmissionLocation
import com.salticusteam.vifi.core.model.SubmissionTier
import com.salticusteam.vifi.core.model.UserProfile

/** Which list is open over the form: a bundled list (list-only), the archive lessons, or the exam names. */
enum class AddExamPicker { UNIVERSITY, FACULTY, DEPARTMENT, LESSON, EXAM_NAME }

/**
 * The "Sınav Ekle" form (iOS AddExamViewModel's fields). University / faculty / department come from the bundled
 * lists, pre-filled from the profile; the lesson from the archive or typed in; the exam name from
 * [ExamNameCatalog]. [picker] is the list open over the form, `null` while the form itself is shown.
 */
data class AddExamUiState(
    /** The signed-in user's uid; an empty value keeps the form from submitting. */
    val ownerId: String = "",
    val university: CatalogSelection = CatalogSelection.EMPTY,
    val faculty: CatalogSelection = CatalogSelection.EMPTY,
    val department: CatalogSelection = CatalogSelection.EMPTY,
    val lesson: CatalogSelection = CatalogSelection.EMPTY,
    val examName: CatalogSelection = CatalogSelection.EMPTY,
    val year: Int = 0,
    /** The years offered by the picker, newest first (2000 through the current year). */
    val yearOptions: List<Int> = emptyList(),
    val kind: ExamKind = ExamKind.IMAGES,
    /** The prepared files in reading order (1-based, contiguous). */
    val files: List<SubmissionFile> = emptyList(),
    val catalog: UniversityCatalog = UniversityCatalog.EMPTY,
    val picker: AddExamPicker? = null,
    val isWorking: Boolean = false,
    /**
     * Set while a queued submit uploads in the background: the user may leave, the upload goes on and its outcome
     * is notified.
     */
    val isSendingInBackground: Boolean = false,
    /** Set while picked files are being read or transcoded, so submitting waits for them. */
    val isPreparingFiles: Boolean = false,
    val error: String? = null,
    /** The latest remote reward values, `null` while unknown or not configured. */
    val rewards: RewardsConfig? = null,
    /** The novelty tier estimated for the current placement, `null` while incomplete or unresolved. */
    val estimatedTier: SubmissionTier? = null,
    /** Whether the user ticked the terms of use; a submission is only sent once they accepted them. */
    val termsAccepted: Boolean = false,
) {
    /** The validated submission, or `null` while any field is missing or invalid. */
    val builtSubmission: ExamSubmission? get() = AddExamValidation.build(this)

    val canSubmit: Boolean get() = !isWorking && !isPreparingFiles && termsAccepted && builtSubmission != null

    /** Whether the user may edit the form (nothing is being sent). */
    val isEditable: Boolean get() = !isWorking

    /** Whether the form may be closed: not while a submit is still being handed to the background queue. */
    val canClose: Boolean get() = isEditable || isSendingInBackground

    /** The lesson row needs a department to look the archive lessons up under. */
    val canPickLesson: Boolean get() = isEditable && department.name.isNotEmpty()

    /** The placement the tier is estimated for, or `null` until every level has been chosen. */
    val location: SubmissionLocation?
        get() {
            val names = listOf(university.name, faculty.name, department.name, lesson.name, examName.name)
            if (names.any { it.isEmpty() }) return null
            return SubmissionLocation(
                university = university.name,
                faculty = faculty.name,
                department = department.name,
                lesson = lesson.name,
                examName = examName.name,
                year = year,
            )
        }

    /** The coin note: tier-specific once placed and configured, else the flat amount, else a generic promise. */
    val coinNote: CoinNote
        get() {
            val tier = estimatedTier
            val tierCoins = tier?.let { rewards?.coins(it) }
            val flatCoins = rewards?.examUploadCoins
            return when {
                tier != null && tierCoins != null -> CoinNote.Estimated(tierCoins, tier)
                flatCoins != null -> CoinNote.Flat(flatCoins)
                else -> CoinNote.Generic
            }
        }

    /** The value currently held by [picker]'s row. */
    fun selection(picker: AddExamPicker): CatalogSelection = when (picker) {
        AddExamPicker.UNIVERSITY -> university
        AddExamPicker.FACULTY -> faculty
        AddExamPicker.DEPARTMENT -> department
        AddExamPicker.LESSON -> lesson
        AddExamPicker.EXAM_NAME -> examName
    }

    /** The fixed options of a list picker; the lesson picker loads its own from the archive. */
    fun options(picker: AddExamPicker): List<CatalogItem> = when (picker) {
        AddExamPicker.UNIVERSITY -> catalog.universities
        AddExamPicker.FACULTY -> catalog.faculties
        AddExamPicker.DEPARTMENT -> catalog.departments
        AddExamPicker.EXAM_NAME -> ExamNameCatalog.names
        AddExamPicker.LESSON -> emptyList()
    }

    companion object {
        /** The first year offered by the picker (the model accepts [ExamSubmission.YEAR_RANGE]). */
        const val FIRST_PICKER_YEAR = 2000

        /** A fresh form for [ownerId], pre-filled from [profile] when there is one. */
        fun initial(ownerId: String, profile: UserProfile?, currentYear: Int) = AddExamUiState(
            ownerId = ownerId,
            university = profile?.let {
                CatalogSelection.stored(it.university, it.universityId, it.universityIsCustom)
            } ?: CatalogSelection.EMPTY,
            faculty = profile?.let {
                CatalogSelection.stored(it.faculty, it.facultyId, it.facultyIsCustom)
            } ?: CatalogSelection.EMPTY,
            department = profile?.let {
                CatalogSelection.stored(it.department, it.departmentId, it.departmentIsCustom)
            } ?: CatalogSelection.EMPTY,
            year = currentYear,
            yearOptions = (FIRST_PICKER_YEAR..currentYear).reversed().toList(),
        )
    }
}

/** What the coin note promises (iOS `coinNoteText`'s three branches). */
sealed interface CoinNote {
    /** An approximate, tier-specific amount: the authoritative tier is decided at approval. */
    data class Estimated(val coins: Int, val tier: SubmissionTier) : CoinNote

    /** The flat amount configured for every approved submission. */
    data class Flat(val coins: Int) : CoinNote

    /** No amount is configured: a generic promise. */
    data object Generic : CoinNote
}

/** One-shot results of the form. */
sealed interface AddExamEvent {
    /** The submission was stored as pending; the screen closes. */
    data object Submitted : AddExamEvent
}
