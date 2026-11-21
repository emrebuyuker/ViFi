package com.salticusteam.vifi.core.data.firebase

import com.salticusteam.vifi.core.data.repository.fake.SampleQuestions
import com.salticusteam.vifi.core.model.QuestionAnswer
import com.salticusteam.vifi.core.model.QuestionFigure
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `StructuredExamParserTests` › `ExamQuestionParser`, case for case (Firestore numbers arrive as `Long`). */
class ExamQuestionParserTest {

    private fun doc(id: String, overrides: Map<String, Any?> = emptyMap()): Pair<String, Map<String, Any?>> {
        val data = mapOf<String, Any?>(
            "schemaVersion" to 1L, "examId" to "e1", "order" to 1L, "label" to "1",
            "text" to "  \$x^2\$ nedir?  ", "speech" to "x kare nedir?",
            "answer" to mapOf("text" to "\$4\$", "choice" to "B"),
            "choices" to listOf(mapOf("label" to "A", "text" to "2"), mapOf("label" to "B", "text" to "4"), mapOf("label" to "", "text" to "boş")),
            "figures" to listOf(mapOf("storagePath" to "questionFigures/exam-e1/001-1.jpg", "width" to 800L, "height" to 0L), mapOf("width" to 1L)),
            "solution" to listOf("adım", "", 3L), "topics" to listOf("cebir"), "difficulty" to 7L,
        )
        return id to (data + overrides)
    }

    @Test
    fun `a published question parses with trimming, defaults and invalid entries dropped`() {
        val question = ExamQuestionParser.questions(listOf(doc("e1-001")), "fallback").first()
        assertEquals("e1-001", question.id)
        assertEquals("e1", question.examId)
        assertEquals("\$x^2\$ nedir?", question.text)
        assertEquals(listOf("A", "B"), question.choices.map { it.label })
        assertEquals(listOf(QuestionFigure("questionFigures/exam-e1/001-1.jpg", 800, null)), question.figures)
        assertEquals(QuestionAnswer("\$4\$", "B", null), question.answer)
        assertEquals(listOf("adım"), question.solution)
        assertEquals(3, question.difficulty)
        assertNull(question.confidence)
        assertFalse(question.needsReview)
    }

    @Test
    fun `questions are ordered by order, drafts take the exam id from the caller and keep review fields`() {
        val docs = listOf(
            doc("002", mapOf("order" to 2L, "examId" to null, "confidence" to 0.4, "needsReview" to true)),
            doc("001", mapOf("order" to 1L, "examId" to null)),
        )
        val questions = ExamQuestionParser.questions(docs, "sub-x")
        assertEquals(listOf("001", "002"), questions.map { it.id })
        assertTrue(questions.all { it.examId == "sub-x" })
        assertEquals(0.4, questions.last().confidence)
        assertTrue(questions.last().needsReview)
    }

    @Test
    fun `drafts carry the regeneration lease and error, blank errors are ignored`() {
        val lease = Instant.ofEpochSecond(2_000)
        val running = ExamQuestionParser.question("001", doc("001", mapOf("regenerationLeaseUntil" to lease, "regenerationError" to "  ")).second, "e1")
        val failed = ExamQuestionParser.question("001", doc("001", mapOf("regenerationError" to " Model yanıt vermedi ")).second, "e1")
        val plain = ExamQuestionParser.question("001", doc("001").second, "e1")

        assertEquals(lease, running?.regeneratingUntil)
        assertNull(running?.regenerationError)
        assertEquals("Model yanıt vermedi", failed?.regenerationError)
        assertNull(failed?.regeneratingUntil)
        assertNull(plain?.regeneratingUntil)
        assertNull(plain?.regenerationError)
        val lease1 = requireNotNull(running)
        assertTrue(lease1.isRegenerating(Instant.ofEpochSecond(1_999)))
        assertFalse(lease1.isRegenerating(Instant.ofEpochSecond(2_000)))
    }

    @Test
    fun `unusable documents are skipped - no text, no answer, bad order, newer schema`() {
        val docs = listOf(
            doc("a", mapOf("text" to "   ")),
            doc("b", mapOf("answer" to mapOf("choice" to "A"))),
            doc("c", mapOf("order" to 0L)),
            doc("d", mapOf("schemaVersion" to 2L)),
            doc("e", mapOf("order" to 5L)),
        )
        assertEquals(listOf("e"), ExamQuestionParser.questions(docs, "e1").map { it.id })
    }

    @Test
    fun `missing label and speech fall back to the order and the text`() {
        val question = ExamQuestionParser.question("x", doc("x", mapOf("label" to null, "speech" to null, "order" to 4L)).second, "e1")
        assertNotNull(question)
        assertEquals("4", question?.label)
        assertEquals(question?.text, question?.speech)
    }

    @Test
    fun `the sample questions parse (mock data)`() {
        val questions = SampleQuestions.questions("demo")
        assertEquals(3, questions.size)
        assertEquals(listOf("demo-001", "demo-002", "demo-003"), questions.map { it.id })
        assertEquals("A", questions[1].answer.choice)
        assertEquals(1, questions[2].figures.size)
        assertEquals("3a", questions[2].label)
    }
}
