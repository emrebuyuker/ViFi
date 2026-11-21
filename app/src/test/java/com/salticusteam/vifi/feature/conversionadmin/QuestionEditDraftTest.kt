package com.salticusteam.vifi.feature.conversionadmin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `QuestionEditDraftTests` (in `ExamConversionCallsTests`). */
class QuestionEditDraftTest {

    private val question = ConversionFixture.question()

    @Test
    fun `an untouched draft is valid and round-trips the question`() {
        val draft = QuestionEditDraft(question)

        assertTrue(draft.isValid)
        val applied = draft.applied(question)
        assertEquals(question.text, applied.text)
        assertEquals(question.choices, applied.choices)
        assertEquals(question.answer, applied.answer)
        assertEquals(question.solution, applied.solution)
        assertEquals(question.figures, applied.figures)
        assertEquals(question.confidence, applied.confidence)
    }

    @Test
    fun `empty text or answer is invalid`() {
        var draft = QuestionEditDraft(question).copy(text = "  ")
        assertFalse(draft.isValid)
        assertEquals(DraftProblem.TEXT_EMPTY, draft.problem)
        draft = draft.copy(text = "x", answerText = "")
        assertFalse(draft.isValid)
        assertEquals(DraftProblem.ANSWER_EMPTY, draft.problem)
    }

    @Test
    fun `the answer choice must be one of the choices`() {
        var draft = QuestionEditDraft(question).copy(answerChoice = "Z")
        assertFalse(draft.isValid)
        assertEquals(DraftProblem.ANSWER_CHOICE_UNKNOWN, draft.problem)
        draft = draft.copy(answerChoice = "")
        assertTrue(draft.isValid)
        assertNull(draft.applied(question).answer.choice)
    }

    @Test
    fun `blank choices and steps are dropped, an empty speech falls back to the text`() {
        val draft = QuestionEditDraft(question).let {
            it.copy(
                choices = it.choices + QuestionEditDraft.Choice(),
                solution = it.solution + "   ",
                speech = "",
            )
        }

        val applied = draft.applied(question)

        assertTrue(draft.isValid)
        assertEquals(2, applied.choices.size)
        assertEquals(listOf("2 + 2 = 4"), applied.solution)
        assertEquals(applied.text, applied.speech)
    }

    @Test
    fun `a half-filled choice is invalid`() {
        val draft = QuestionEditDraft(question).let { it.copy(choices = it.choices + QuestionEditDraft.Choice("C", "")) }

        assertFalse(draft.isValid)
        assertEquals(DraftProblem.CHOICE_INCOMPLETE, draft.problem)
    }

    @Test
    fun `the server's limits are enforced`() {
        val base = QuestionEditDraft(question)

        assertEquals(DraftProblem.TEXT_TOO_LONG, base.copy(text = "x".repeat(QuestionEditDraft.Limit.TEXT + 1)).problem)
        assertEquals(DraftProblem.ANSWER_TOO_LONG, base.copy(answerText = "x".repeat(QuestionEditDraft.Limit.ANSWER_TEXT + 1)).problem)
        assertEquals(DraftProblem.STEP_TOO_LONG, base.copy(solution = listOf("x".repeat(QuestionEditDraft.Limit.SOLUTION_STEP + 1))).problem)
        assertEquals(
            DraftProblem.TOO_MANY_ITEMS,
            base.copy(solution = List(QuestionEditDraft.Limit.SOLUTION_STEPS + 1) { "a" }).problem,
        )
        assertEquals(
            DraftProblem.CHOICE_TOO_LONG,
            base.copy(choices = listOf(QuestionEditDraft.Choice("ABCDE", "x")), answerChoice = "").problem,
        )
    }

    @Test
    fun `the applied question keeps everything the editor does not touch`() {
        val edited = QuestionEditDraft(question).copy(text = "  Yeni metin  ", answerSpeech = " dört ").applied(question)

        assertEquals("Yeni metin", edited.text)
        assertEquals("dört", edited.answer.speech)
        assertEquals(question.id, edited.id)
        assertEquals(question.order, edited.order)
        assertEquals(question.label, edited.label)
        assertEquals(question.needsReview, edited.needsReview)
        assertEquals(question.topics, edited.topics)
    }
}
