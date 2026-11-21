package com.salticusteam.vifi.feature.conversionadmin

import androidx.annotation.StringRes
import androidx.compose.runtime.saveable.Saver
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.QuestionAnswer
import com.salticusteam.vifi.core.model.QuestionChoice

/** Why a question edit cannot be saved yet (iOS `QuestionEditDraft.validationMessage`); the screen shows its text. */
enum class DraftProblem(@get:StringRes val messageRes: Int) {
    TEXT_EMPTY(R.string.conversion_admin_edit_problem_text_empty),
    TEXT_TOO_LONG(R.string.conversion_admin_edit_problem_text_long),
    ANSWER_EMPTY(R.string.conversion_admin_edit_problem_answer_empty),
    ANSWER_TOO_LONG(R.string.conversion_admin_edit_problem_answer_long),
    TOO_MANY_ITEMS(R.string.conversion_admin_edit_problem_too_many),
    CHOICE_INCOMPLETE(R.string.conversion_admin_edit_problem_choice_incomplete),
    CHOICE_TOO_LONG(R.string.conversion_admin_edit_problem_choice_long),
    STEP_TOO_LONG(R.string.conversion_admin_edit_problem_step_long),
    ANSWER_CHOICE_UNKNOWN(R.string.conversion_admin_edit_problem_answer_choice),
}

/**
 * The editable state of one question's content while an admin edits it (a draft before publishing, or a published
 * question, K15): text, speech, choices, the answer and the solution steps (iOS `QuestionEditDraft`).
 *
 * Pure and view-free so the validation and the round trip back to an [ExamQuestion] are unit-tested. Limits mirror
 * `LIMITS` in `firebase/functions/conversions/schema.js`.
 */
data class QuestionEditDraft(
    val text: String,
    val speech: String,
    val choices: List<Choice>,
    val answerText: String,
    /** The correct choice label; empty for a non-multiple-choice answer. */
    val answerChoice: String,
    val answerSpeech: String,
    val solution: List<String>,
) {

    /** One answer option being edited; both fields may be blank while typing (a blank option is dropped on save). */
    data class Choice(val label: String = "", val text: String = "")

    constructor(question: ExamQuestion) : this(
        text = question.text,
        speech = question.speech,
        choices = question.choices.map { Choice(it.label, it.text) },
        answerText = question.answer.text,
        answerChoice = question.answer.choice.orEmpty(),
        answerSpeech = question.answer.speech.orEmpty(),
        solution = question.solution,
    )

    /** The first problem that stops saving, or `null` when the draft is valid. */
    val problem: DraftProblem?
        get() {
            if (text.isBlank()) return DraftProblem.TEXT_EMPTY
            if (text.length > Limit.TEXT || speech.length > Limit.SPEECH) return DraftProblem.TEXT_TOO_LONG
            if (answerText.isBlank()) return DraftProblem.ANSWER_EMPTY
            if (answerText.length > Limit.ANSWER_TEXT) return DraftProblem.ANSWER_TOO_LONG
            if (choices.size > Limit.CHOICES || solution.size > Limit.SOLUTION_STEPS) return DraftProblem.TOO_MANY_ITEMS
            val filled = choices.filter { it.label.isNotBlank() || it.text.isNotBlank() }
            if (filled.any { it.label.isBlank() || it.text.isBlank() }) return DraftProblem.CHOICE_INCOMPLETE
            if (filled.any { it.label.length > Limit.CHOICE_LABEL || it.text.length > Limit.CHOICE_TEXT }) {
                return DraftProblem.CHOICE_TOO_LONG
            }
            if (solution.any { it.length > Limit.SOLUTION_STEP }) return DraftProblem.STEP_TOO_LONG
            val label = answerChoice.trim()
            if (label.isNotEmpty() && filled.none { it.label.trim() == label }) return DraftProblem.ANSWER_CHOICE_UNKNOWN
            return null
        }

    val isValid: Boolean get() = problem == null

    /** [question] with this draft's content (blank choices and steps dropped); everything else is kept. */
    fun applied(to: ExamQuestion): ExamQuestion {
        val trimmedText = text.trim()
        val label = answerChoice.trim()
        val answerSpeechText = answerSpeech.trim()
        return to.copy(
            text = trimmedText,
            speech = speech.trim().ifEmpty { trimmedText },
            choices = choices
                .map { QuestionChoice(it.label.trim(), it.text.trim()) }
                .filter { it.label.isNotEmpty() && it.text.isNotEmpty() },
            answer = QuestionAnswer(
                text = answerText.trim(),
                choice = label.ifEmpty { null },
                speech = answerSpeechText.ifEmpty { null },
            ),
            solution = solution.map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    /** The server's limits (`LIMITS` in schema.js). */
    object Limit {
        const val TEXT = 8000
        const val SPEECH = 8000
        const val CHOICES = 10
        const val CHOICE_LABEL = 4
        const val CHOICE_TEXT = 2000
        const val ANSWER_TEXT = 4000
        const val SOLUTION_STEPS = 30
        const val SOLUTION_STEP = 2000
    }

    companion object {
        /** Survives a rotation while the editor is open: flat strings, the lists' sizes first. */
        val Saver: Saver<QuestionEditDraft, ArrayList<String>> = Saver(
            save = { draft ->
                arrayListOf(
                    draft.text, draft.speech, draft.answerText, draft.answerChoice, draft.answerSpeech,
                    draft.choices.size.toString(),
                ).apply {
                    draft.choices.forEach { add(it.label); add(it.text) }
                    addAll(draft.solution)
                }
            },
            restore = { saved ->
                val count = saved[CHOICE_COUNT_INDEX].toInt()
                val choicesEnd = FIRST_CHOICE_INDEX + count * 2
                QuestionEditDraft(
                    text = saved[0],
                    speech = saved[1],
                    answerText = saved[2],
                    answerChoice = saved[3],
                    answerSpeech = saved[4],
                    choices = (FIRST_CHOICE_INDEX until choicesEnd step 2).map { Choice(saved[it], saved[it + 1]) },
                    solution = saved.subList(choicesEnd, saved.size).toList(),
                )
            },
        )

        private const val CHOICE_COUNT_INDEX = 5
        private const val FIRST_CHOICE_INDEX = 6
    }
}
