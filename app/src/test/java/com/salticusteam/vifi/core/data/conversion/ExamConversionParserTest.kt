package com.salticusteam.vifi.core.data.conversion

import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.ConversionSource
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ConversionUsage
import com.salticusteam.vifi.core.model.ExamKind
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `StructuredExamParserTests` › `ExamConversionParser` + `ExamConversionCallsTests.parsesFilesAndSubject`. */
class ExamConversionParserTest {

    private val base = mapOf<String, Any?>("source" to mapOf("type" to "legacy", "examId" to "e"), "kind" to "JPG", "status" to "ready")

    @Test
    fun `a job document parses its source, status, provider, usage and placement`() {
        val data = mapOf<String, Any?>(
            "source" to mapOf("type" to "submission", "submissionId" to "s1"), "kind" to "PDF", "status" to "ready",
            "model" to mapOf("provider" to "gemini", "id" to "gemini-pro"), "attempts" to 2L, "error" to "",
            "usage" to mapOf("inputTokens" to 1_000L, "outputTokens" to 200L, "costUsd" to 0.31),
            "placement" to mapOf("names" to listOf("Bozok", "Müh", "Bilg", "Mat", "2019 FİNAL"), "ids" to listOf("a")),
            "createdAt" to Instant.ofEpochSecond(10),
        )
        val job = requireNotNull(ExamConversionParser.conversion("sub-s1", data))
        assertEquals(ConversionSource.Submission("s1"), job.source)
        assertEquals("sub-s1", job.source.jobId)
        assertEquals(ExamKind.PDF, job.kind)
        assertEquals(ConversionStatus.READY, job.status)
        assertEquals(AIProvider.GEMINI, job.provider)
        assertEquals(2, job.attempts)
        assertNull(job.error)
        assertEquals(ConversionUsage(1_000, 200, 0.31), job.usage)
        assertEquals("2019 FİNAL", job.placementNames.last())
        assertEquals(Instant.ofEpochSecond(10), job.createdAt)
    }

    @Test
    fun `a legacy job builds the exam- job id, unknown providers stay null`() {
        val data = mapOf<String, Any?>(
            "source" to mapOf("type" to "legacy", "examId" to "e9"), "kind" to "JPG", "status" to "queued",
            "model" to mapOf("provider" to "gpt"),
        )
        val job = requireNotNull(ExamConversionParser.conversion("exam-e9", data))
        assertEquals("exam-e9", job.source.jobId)
        assertNull(job.provider)
        assertNull(job.usage)
        assertFalse(job.status.isTerminal)
    }

    @Test
    fun `the placement carries the original title and the list suggestion (K28)`() {
        val names = mapOf("names" to listOf("U", "F", "D", "C", "2019 FİZİK LAB"))

        val stored = ExamConversionParser.conversion(
            "x", base + ("placement" to names + mapOf("originalTitle" to "2019 FİZİK LAB", "suggestedTitle" to "2019 LABORATUVAR")),
        )
        assertEquals("2019 FİZİK LAB", stored?.originalTitle)
        assertEquals("2019 LABORATUVAR", stored?.suggestedTitle)

        val none = ExamConversionParser.conversion("x", base + ("placement" to mapOf("originalTitle" to "DERS NOTU", "suggestedTitle" to null)))
        assertEquals("DERS NOTU", none?.originalTitle)
        assertNull(none?.suggestedTitle)

        // A job created before K28 has no suggestion field: it is computed from the exam name, as on the server.
        val old = ExamConversionParser.conversion("x", base + ("placement" to names))
        assertEquals("2019 FİZİK LAB", old?.originalTitle)
        assertEquals("2019 LABORATUVAR", old?.suggestedTitle)

        // A stored suggestion that is not a list title is ignored.
        val bad = ExamConversionParser.conversion("x", base + ("placement" to mapOf("suggestedTitle" to "2019 FİZİK")))
        assertNull(bad?.suggestedTitle)
    }

    @Test
    fun `jobs without a valid source, kind or status are skipped`() {
        assertNotNull(ExamConversionParser.conversion("x", base))
        assertNull(ExamConversionParser.conversion("x", base + ("source" to mapOf("type" to "legacy"))))
        assertNull(ExamConversionParser.conversion("x", base + ("kind" to "DOC")))
        assertNull(ExamConversionParser.conversion("x", base + ("status" to "done")))
    }

    @Test
    fun `server job documents parse their files and the suggested subject`() {
        val job = ExamConversionParser.conversion(
            "sub-1",
            mapOf(
                "source" to mapOf("type" to "submission", "submissionId" to "1"), "kind" to "JPG", "status" to "ready",
                "files" to listOf(mapOf("storagePath" to "imagess/b.jpg", "order" to 2L), mapOf("storagePath" to "imagess/a.jpg", "order" to 1L)),
                "placement" to mapOf("names" to listOf("U"), "subjectId" to "calculus", "subjectName" to "Calculus"),
            ),
        )
        assertEquals(2, job?.files?.size)
        assertEquals("calculus", job?.suggestedSubjectId)
        assertEquals("Calculus", job?.suggestedSubjectName)
    }

    @Test
    fun `a whole-number cost and a null error parse (live shape, SOPS-01)`() {
        val job = ExamConversionParser.conversion(
            "x", base + mapOf("usage" to mapOf("inputTokens" to 10L, "outputTokens" to 5L, "costUsd" to 1L), "error" to null),
        )
        assertEquals(1.0, job?.usage?.costUsd)
        assertNull(job?.error)
    }
}
