package com.salticusteam.vifi.feature.practice

import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PoolIndex
import com.salticusteam.vifi.core.model.PracticeScope
import com.salticusteam.vifi.core.model.PracticeSelection

// Added in 4.2.0 — a practice type of the setup with the question count of its largest scope.
data class PracticeTypeRow(
    val type: ExamType,
    /** The largest scope's count (Türkiye geneli includes the others). */
    val count: Int,
)

// Added in 4.2.0 — a pool scope of one type with its question count.
data class PracticeScopeRow(
    val scope: PracticeScope,
    val count: Int,
)

/**
 * The setup's two steps over a subject's [index]: the exam type, then the scope (iOS `PracticeSetupViewModel`).
 * Pure state; the screen steps through it. [universityId] is the signed-in user's university from the profile; a blank
 * or unknown one hides the "Üniversitem" scope.
 *
 * Added in 4.2.0 — the setup's choice logic.
 */
class PracticeSetupChoices(
    val index: PoolIndex,
    val courseId: String,
    universityId: String?,
) {
    val universityId: String? = universityId?.takeIf { it.isNotEmpty() }

    /** The practice types the subject has questions for, in the setup's display order. */
    val typeRows: List<PracticeTypeRow> = ExamType.PRACTICE_DISPLAY_ORDER.mapNotNull { type ->
        val largest = PracticeScope.entries.maxOf { count(type, it) }
        if (largest > 0) PracticeTypeRow(type, largest) else null
    }

    /**
     * The scopes of [type] that hold questions, each with its count (one question is enough, P4). "Üniversitem" is
     * left out without a known university.
     */
    fun scopeRows(type: ExamType): List<PracticeScopeRow> = PracticeScope.entries.mapNotNull { scope ->
        val count = count(type, scope)
        if (count > 0) PracticeScopeRow(scope, count) else null
    }

    fun selection(type: ExamType, scope: PracticeScope): PracticeSelection = PracticeSelection(
        subjectId = index.subjectId,
        examType = type,
        scope = scope,
        courseId = courseId,
        universityId = universityId,
        poolCount = count(type, scope),
    )

    private fun count(type: ExamType, scope: PracticeScope): Int =
        index.count(type, scope, courseId, universityId)
}
