package com.salticusteam.vifi.feature.practice

import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PracticeSelection

/**
 * The "Sınava gir" setup (iOS `PracticeFlowView`): step one lists the exam types, step two the scopes of the chosen
 * type, each with its question count.
 */
data class PracticeSetupUiState(
    /** The subject's pool index is being read. */
    val isLoading: Boolean = true,
    /** The index could not be read (Turkish message); `null` otherwise. */
    val error: String? = null,
    /** Step one: the types that hold questions. */
    val typeRows: List<PracticeTypeRow> = emptyList(),
    /** The type picked in step one; `null` while step one is showing. */
    val selectedType: ExamType? = null,
    /** Step two: the scopes of [selectedType] that hold questions. */
    val scopeRows: List<PracticeScopeRow> = emptyList(),
) {
    /** The index was read but no type has a question (the entry normally hides itself before this). */
    val isEmpty: Boolean get() = !isLoading && error == null && typeRows.isEmpty()
}

/** One-shot results of the setup. */
sealed interface PracticeSetupEvent {
    /** A scope was picked: open the session over this pool. */
    data class StartSession(val selection: PracticeSelection) : PracticeSetupEvent
}
