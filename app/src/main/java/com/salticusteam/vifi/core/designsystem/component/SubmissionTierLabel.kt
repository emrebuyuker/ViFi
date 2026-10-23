package com.salticusteam.vifi.core.designsystem.component

import androidx.annotation.StringRes
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.SubmissionTier

/** The Turkish label of [SubmissionTier] (iOS `SubmissionTier.label`), shared by add-exam and moderation. */
@StringRes
fun SubmissionTier.labelResId(): Int = when (this) {
    SubmissionTier.NEW_UNIVERSITY -> R.string.submission_tier_new_university
    SubmissionTier.NEW_FACULTY -> R.string.submission_tier_new_faculty
    SubmissionTier.NEW_DEPARTMENT -> R.string.submission_tier_new_department
    SubmissionTier.NEW_COURSE -> R.string.submission_tier_new_course
    SubmissionTier.NEW_EXAM -> R.string.submission_tier_new_exam
    SubmissionTier.DUPLICATE -> R.string.submission_tier_duplicate
}
