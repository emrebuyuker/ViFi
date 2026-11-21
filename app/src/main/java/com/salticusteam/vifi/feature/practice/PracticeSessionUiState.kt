package com.salticusteam.vifi.feature.practice

import com.salticusteam.vifi.core.model.PracticeQuestion
import com.salticusteam.vifi.core.model.PracticeSelection

// Added in 4.2.0 — what a practice session is showing (iOS `PracticeSessionViewModel.Phase`).
sealed interface PracticePhase {
    /** Waiting for a question. */
    data object Loading : PracticePhase

    /** A question is on screen ([PracticeSessionUiState.current]). */
    data object Question : PracticePhase

    /** Every question of the pool was seen (the server returned none for the remaining ids): "Baştan başla". */
    data object Exhausted : PracticePhase

    /** The free daily limit is used up; the paywall is offered. */
    data object LimitReached : PracticePhase

    /** A question could not be fetched; [message] is Turkish, "Tekrar Dene" asks again. */
    data class Failed(val message: String) : PracticePhase
}

/**
 * One practice session: questions one at a time from the server, no right/wrong tracking and no end summary (owner
 * decisions P2, P3). The session screen (SIT-01) draws every field from here.
 */
data class PracticeSessionUiState(
    val selection: PracticeSelection,
    val phase: PracticePhase = PracticePhase.Loading,
    /** The question on screen; `null` before the first one and on the limit and exhausted screens. */
    val current: PracticeQuestion? = null,
    val isAnswerRevealed: Boolean = false,
    /** The running number of the question on screen in this session ("Soru 1, 2, 3…"); 0 before the first one. */
    val questionNumber: Int = 0,
    /** The pool's size as the server last reported it. */
    val poolSize: Int = selection.poolCount,
    /** The free questions the user still has today ("Bugün kalan: N soru"); `null` for Premium (no counter). */
    val remainingToday: Int? = null,
)

/** One-shot results of the session. */
sealed interface PracticeSessionEvent {
    /** "Premium'a Göz At" was tapped on the limit screen: open the paywall. */
    data object OpenPaywall : PracticeSessionEvent
}
