package com.salticusteam.vifi.core.data.conversion

import com.salticusteam.vifi.core.model.AIMonthlyUsage
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.BulkConversionEstimate
import com.salticusteam.vifi.core.model.BulkConversionScope
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.PublishedExamResult
import com.salticusteam.vifi.core.model.QuestionAnswer
import com.salticusteam.vifi.core.model.QuestionChoice
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `ExamConversionCallsTests` (the edit-draft and publish-plan suites belong to ADX-01 / ADM-01). */
class ExamConversionCallsTest {

    private fun question(order: Int = 1) = ExamQuestion(
        id = "%03d".format(order), examId = "sub-s1", order = order, label = order.toString(),
        text = "2 + 2 kaçtır?", speech = "iki artı iki kaçtır",
        choices = listOf(QuestionChoice("A", "3"), QuestionChoice("B", "4")),
        figures = emptyList(), answer = QuestionAnswer("4", "B", null), solution = listOf("2 + 2 = 4"),
        topics = listOf("toplama"), difficulty = 1, confidence = 0.9,
    )

    @Test
    fun `callable names match the server contract`() {
        assertEquals("requestExamConversion", ExamConversionCalls.Name.REQUEST_CONVERSION)
        assertEquals("requestBulkConversion", ExamConversionCalls.Name.REQUEST_BULK)
        assertEquals("regenerateExamConversion", ExamConversionCalls.Name.REGENERATE)
        assertEquals("regenerateDraftQuestion", ExamConversionCalls.Name.REGENERATE_DRAFT_QUESTION)
        assertEquals("publishExamConversion", ExamConversionCalls.Name.PUBLISH)
        assertEquals("discardExamConversion", ExamConversionCalls.Name.DISCARD)
        assertEquals("updatePublishedQuestion", ExamConversionCalls.Name.UPDATE_PUBLISHED_QUESTION)
        assertEquals("regeneratePublishedQuestion", ExamConversionCalls.Name.REGENERATE_PUBLISHED_QUESTION)
    }

    @Test
    fun `request payloads carry the provider and ids as documented`() {
        val single = ExamConversionCalls.requestConversion("e1", AIProvider.CLAUDE)
        assertEquals("e1", single["examId"])
        assertEquals("claude", single["provider"])

        val course = ExamConversionCalls.requestBulk(BulkConversionScope.Course("c1"), AIProvider.GEMINI, dryRun = true)
        assertEquals("c1", course["courseId"])
        assertNull(course["departmentId"])
        assertEquals(true, course["dryRun"])
        assertEquals("gemini", course["provider"])

        val department = ExamConversionCalls.requestBulk(BulkConversionScope.Department("d1"), AIProvider.CLAUDE, dryRun = false)
        assertEquals("d1", department["departmentId"])
        assertEquals(false, department["dryRun"])

        val draft = ExamConversionCalls.regenerateDraftQuestion("sub-1", 3, AIProvider.GEMINI)
        assertEquals("sub-1", draft["jobId"])
        assertEquals(3, draft["order"])
    }

    @Test
    fun `publishing leaves blank subject fields and a blank title out`() {
        val blank = ExamConversionCalls.publish("j", subjectId = "  ", newSubjectName = null, examTitle = " ")
        assertEquals(listOf("jobId"), blank.keys.sorted())

        val full = ExamConversionCalls.publish("j", subjectId = "s", newSubjectName = " Yeni ", examTitle = "2019 VİZE")
        assertEquals("s", full["subjectId"])
        assertEquals("Yeni", full["newSubjectName"])
        assertEquals("2019 VİZE", full["examTitle"])
    }

    @Test
    fun `question content fields are the editable ones only`() {
        val fields = ExamConversionCalls.contentFields(question())

        assertEquals(listOf("answer", "choices", "solution", "speech", "text"), fields.keys.sorted())
        val answer = fields["answer"] as Map<*, *>
        assertEquals("4", answer["text"])
        assertEquals("B", answer["choice"])
        assertFalse(answer.containsKey("speech"))
        assertEquals(mapOf("label" to "A", "text" to "3"), (fields["choices"] as List<*>).first())

        assertEquals("002", ExamConversionCalls.updatePublishedQuestion(question(order = 2))["questionId"])
    }

    @Test
    fun `responses are parsed leniently`() {
        assertEquals(ConversionStatus.QUEUED, ExamConversionCalls.status(mapOf("status" to "queued")))
        assertNull(ExamConversionCalls.status(mapOf("status" to "nope")))
        assertEquals("exam-1", ExamConversionCalls.jobId(mapOf("jobId" to "exam-1")))

        assertEquals(
            BulkConversionEstimate(4, 1.5, listOf("a", "b")),
            ExamConversionCalls.bulkEstimate(mapOf("examCount" to 4, "estimatedCostUsd" to 1.5, "jobIds" to listOf("a", "b"))),
        )
        assertEquals(BulkConversionEstimate(0, 0.0, emptyList()), ExamConversionCalls.bulkEstimate(null))

        assertEquals(PublishedExamResult("e", 20), ExamConversionCalls.publishResult(mapOf("examId" to "e", "questionCount" to 20)))
        assertNull(ExamConversionCalls.publishResult(mapOf("questionCount" to 20)))
    }

    @Test
    fun `monthly usage reads costUsd and treats a missing document as zero`() {
        assertEquals(12.5, ExamConversionCalls.monthlyUsage("2026-11", mapOf("costUsd" to 12.5)).costUsd, 0.0)
        assertEquals(0.0, ExamConversionCalls.monthlyUsage("2026-11", null).costUsd, 0.0)
        val usage = AIMonthlyUsage("2026-11", 40.0)
        assertEquals(0.8, usage.fraction, 1e-9)
        assertTrue(usage.exceedsCap(adding = 11.0))
        assertFalse(usage.exceedsCap(adding = 10.0))
    }

    @Test
    fun `the month key follows Europe Istanbul (UTC+3)`() {
        // 31 Oct 2026 22:00 UTC is already 1 Nov 01:00 in Istanbul.
        val instant = Instant.ofEpochSecond(1_793_484_000)
        assertEquals("2026-11", AIMonthlyUsage.monthKey(instant))
        assertEquals("2026-10", AIMonthlyUsage.monthKey(instant.minusSeconds(4 * 3600)))
    }
}
