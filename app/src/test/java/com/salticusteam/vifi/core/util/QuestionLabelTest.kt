package com.salticusteam.vifi.core.util

import com.salticusteam.vifi.core.data.firebase.ExamQuestionParser
import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `QuestionLabelTests` = server `LABEL_CASES` (conversionSchema.test.mjs). */
class QuestionLabelTest {

    private val cases = listOf(
        "Soru 1)" to "1", "1" to "1", "1." to "1", "1)" to "1", "(3)" to "3", "S1" to "1", "S.1" to "1", "soru 5" to "5",
        "SORU 12:" to "12", "Soru No: 4" to "4", "Soru 2a)" to "2a", "2a)" to "2a", "2 a" to "2a", "2-b" to "2b",
        "2.c" to "2c", "3A" to "3A", "  7  " to "7", "Ek Soru" to "Ek Soru", "Bonus" to "Bonus",
    )

    @Test
    fun `each shared case normalizes like the server`() {
        assertEquals(19, cases.size)
        for ((input, expected) in cases) assertEquals(input, expected, QuestionLabel.normalize(input))
    }

    @Test
    fun `normalizing is idempotent`() {
        for ((input, _) in cases) {
            val once = QuestionLabel.normalize(input)
            assertEquals(once, QuestionLabel.normalize(once))
        }
    }

    @Test
    fun `the parser reads a published Soru 1) label as 1`() {
        val question = ExamQuestionParser.question(
            "e-001",
            mapOf("order" to 1L, "label" to "Soru 1)", "text" to "Soru metni", "answer" to mapOf("text" to "Cevap")),
            "e",
        )
        assertEquals("1", question?.label)
    }
}
