package com.salticusteam.vifi.core.data.practice

import com.salticusteam.vifi.core.data.firebase.FirestoreArchiveParser
import com.salticusteam.vifi.core.data.repository.fake.SampleQuestions
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PoolIndex
import com.salticusteam.vifi.core.model.PracticeBatch
import com.salticusteam.vifi.core.model.PracticeLimits
import com.salticusteam.vifi.core.model.PracticeQuestion
import com.salticusteam.vifi.core.model.PracticeRequest
import com.salticusteam.vifi.core.model.PracticeScope
import com.salticusteam.vifi.core.model.PracticeSelection
import com.salticusteam.vifi.core.model.SubscriptionProof
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `PracticeParserTests`, case for case; the payload cases use the Android proof `{platform, purchaseToken, productId}`. */
class PracticeParserTest {

    private val selection = PracticeSelection(
        subjectId = "topoloji-1", examType = ExamType.FINAL, scope = PracticeScope.TURKEY,
        courseId = "topoloji-1-kocaeli", universityId = "kocaeli-universitesi", poolCount = 12,
    )

    private fun questionData(id: String? = "e1-002", order: Int = 2, extra: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>(
            "examId" to "e1", "order" to order, "text" to "Soru?", "answer" to mapOf("text" to "Cevap"),
            "examTitle" to "2019 FİNAL", "universityName" to "Bozok Üniversitesi",
        )
        if (id != null) data["id"] = id
        return data + extra
    }

    // --- Index ---

    @Test
    fun `the index keeps practice types with positive counts per scope`() {
        val data = mapOf<String, Any?>(
            "name" to "Topoloji 1",
            "types" to mapOf(
                "final" to mapOf("tr" to 40L, "u-bozok" to 12L, "c-top1" to 3L, "c-zero" to 0L),
                "vize" to mapOf("tr" to 1L),
                "ara-sinav" to mapOf("tr" to 0L),
                "odev" to mapOf("tr" to 9L),
                "bilinmeyen" to mapOf("tr" to 5L),
            ),
        )

        val index = PoolIndexParser.index(data, "topoloji-1")

        assertEquals("topoloji-1", index?.subjectId)
        assertEquals("Topoloji 1", index?.name)
        assertEquals(setOf(ExamType.FINAL, ExamType.VIZE), index?.types?.keys)
        assertEquals(mapOf("tr" to 40, "u-bozok" to 12, "c-top1" to 3), index?.types?.get(ExamType.FINAL))
        assertEquals(true, index?.hasPracticeQuestions)
    }

    @Test
    fun `an index without any usable type is no index`() {
        assertNull(PoolIndexParser.index(emptyMap(), "s"))
        assertNull(PoolIndexParser.index(mapOf("types" to emptyMap<String, Any>()), "s"))
        assertNull(PoolIndexParser.index(mapOf("types" to mapOf("odev" to mapOf("tr" to 3L))), "s"))
    }

    @Test
    fun `counts are looked up by scope key, an unknown university has no key`() {
        val index = PoolIndex("topoloji-1", "Topoloji 1", mapOf(ExamType.FINAL to mapOf("tr" to 40, "u-bozok" to 12, "c-top1" to 3)))

        assertEquals(3, index.count(ExamType.FINAL, PracticeScope.COURSE, "top1", null))
        assertEquals(12, index.count(ExamType.FINAL, PracticeScope.UNIVERSITY, "top1", "bozok"))
        assertEquals(0, index.count(ExamType.FINAL, PracticeScope.UNIVERSITY, "top1", null))
        assertEquals(40, index.count(ExamType.FINAL, PracticeScope.TURKEY, "top1", null))
        assertEquals(0, index.count(ExamType.VIZE, PracticeScope.TURKEY, "top1", null))
    }

    // --- Batch ---

    @Test
    fun `a batch parses questions with their source and the limit fields`() {
        val data = mapOf(
            "questions" to listOf(questionData(), questionData(id = "e1-003", order = 3)),
            "poolSize" to 17, "remainingToday" to 3, "limitReached" to false,
        )

        val batch = PracticeParser.batch(data)

        assertEquals(listOf("e1-002", "e1-003"), batch?.questions?.map { it.id })
        assertEquals("2019 FİNAL", batch?.questions?.first()?.examTitle)
        assertEquals("BOZOK ÜNİVERSİTESİ · 2019 FİNAL", batch?.questions?.first()?.sourceLine)
        assertEquals(17, batch?.poolSize)
        assertEquals(3, batch?.remainingToday)
        assertEquals(false, batch?.limitReached)
    }

    @Test
    fun `Premium has no remaining count (null)`() {
        val data = mapOf("questions" to listOf(questionData()), "poolSize" to 5, "remainingToday" to null, "limitReached" to false)
        assertNull(PracticeParser.batch(data)?.remainingToday)
    }

    @Test
    fun `a missing id is rebuilt from the exam id and order, an unreadable question is skipped`() {
        val data = mapOf(
            "questions" to listOf(questionData(id = null, order = 4), mapOf("examId" to "e1", "order" to 5), questionData(id = "e1-009", order = 9)),
            "poolSize" to 3, "remainingToday" to 0, "limitReached" to true,
        )

        val batch = PracticeParser.batch(data)

        assertEquals(listOf("e1-004", "e1-009"), batch?.questions?.map { it.id })
        assertEquals(0, batch?.remainingToday)
        assertEquals(true, batch?.limitReached)
    }

    @Test
    fun `updatedAt arrives as epoch milliseconds, whole, double or null`() {
        val whole = PracticeParser.question(questionData(extra = mapOf("updatedAt" to 1_762_000_000_000L)))
        val fractional = PracticeParser.question(questionData(extra = mapOf("updatedAt" to 1_762_000_000_500.0)))
        val nullValue = PracticeParser.question(questionData(extra = mapOf("updatedAt" to null)))

        assertEquals(Instant.ofEpochSecond(1_762_000_000), whole?.question?.updatedAt)
        assertEquals(Instant.ofEpochMilli(1_762_000_000_500), fractional?.question?.updatedAt)
        assertNotNull(nullValue)
        assertNull(nullValue?.question?.updatedAt)
    }

    @Test
    fun `null enrichment fields leave the source out without dropping the question`() {
        val question = PracticeParser.question(questionData(extra = mapOf("examTitle" to null, "universityName" to null)))

        assertEquals("e1-002", question?.id)
        assertNull(question?.examTitle)
        assertNull(question?.universityName)
        assertNull(question?.sourceLine)
    }

    @Test
    fun `a missing pool is an empty batch, and a limit with a re-sent question is kept as is`() {
        val empty = PracticeParser.batch(mapOf("questions" to emptyList<Any>(), "poolSize" to 0))
        val limited = PracticeParser.batch(mapOf("questions" to listOf(questionData()), "poolSize" to 4, "remainingToday" to 0, "limitReached" to true))

        assertEquals(PracticeBatch(emptyList(), poolSize = 0, remainingToday = null, limitReached = false), empty)
        assertEquals(1, limited?.questions?.size)
        assertEquals(true, limited?.limitReached)
    }

    @Test
    fun `a response that is not a map is rejected`() {
        assertNull(PracticeParser.batch(null))
        assertNull(PracticeParser.batch("oops"))
    }

    @Test
    fun `the source line drops what the server did not send`() {
        val question = SampleQuestions.questions("e1").first()
        assertNull(PracticeQuestion(question, examTitle = null, universityName = null).sourceLine)
        assertEquals("2019 FİNAL", PracticeQuestion(question, examTitle = "2019 FİNAL", universityName = null).sourceLine)
    }

    // --- Callable payload ---

    @Test
    fun `the payload carries the scope's id, the capped exclude list and the Premium proof`() {
        val request = PracticeRequest(selection, exclude = (1..350).map { "e-$it" }, count = 99)
        val proof = SubscriptionProof(purchaseToken = "token", productId = "vifi_premium")

        val body = PracticeCalls.payload(request, proof)

        assertEquals("topoloji-1", body["subjectId"])
        assertEquals("final", body["examType"])
        assertEquals("tr", body["scope"])
        assertNull(body["courseId"])
        assertEquals(PracticeLimits.MAX_BATCH, body["count"])
        val exclude = body["exclude"] as List<*>
        assertEquals(PracticeLimits.MAX_EXCLUDE, exclude.size)
        assertEquals("e-350", exclude.last())
        assertEquals(mapOf("platform" to "android", "purchaseToken" to "token", "productId" to "vifi_premium"), body["subscriptionProof"])
        assertEquals("nextPracticeQuestions", PracticeCalls.NEXT_PRACTICE_QUESTIONS)
    }

    @Test
    fun `course and university scopes send their ids, no proof means no proof key`() {
        val course = PracticeSelection("s", ExamType.VIZE, PracticeScope.COURSE, "c1", "u1", 1)
        val university = PracticeSelection("s", ExamType.VIZE, PracticeScope.UNIVERSITY, "c1", "u1", 1)

        val courseBody = PracticeCalls.payload(PracticeRequest(course, emptyList(), 1), proof = null)
        val universityBody = PracticeCalls.payload(PracticeRequest(university, emptyList(), 1), SubscriptionProof("", "vifi_premium"))

        assertEquals("c1", courseBody["courseId"])
        assertNull(courseBody["universityId"])
        assertEquals("u1", universityBody["universityId"])
        assertFalse(courseBody.containsKey("subscriptionProof"))
        assertFalse(universityBody.containsKey("subscriptionProof"))
    }

    @Test
    fun `the proof never prints its token`() {
        assertFalse(SubscriptionProof("secret-token", "vifi_premium").toString().contains("secret-token"))
    }

    // --- Archive subject ---

    @Test
    fun `a course node's subjectId is read, blank or absent means none`() {
        val path = ArchivePath(listOf("u", "f", "d"))
        val docs = listOf<Pair<String, Map<String, Any?>>>(
            "c1" to mapOf("name" to "Topoloji 1", "subjectId" to "topoloji-1"),
            "c2" to mapOf("name" to "Analiz", "subjectId" to "  "),
            "c3" to mapOf("name" to "Cebir"),
        )

        val items = FirestoreArchiveParser.items(docs, path)

        assertEquals("topoloji-1", items.first { it.name == "Topoloji 1" }.subjectId)
        assertNull(items.first { it.name == "Analiz" }.subjectId)
        assertNull(items.first { it.name == "Cebir" }.subjectId)
        assertTrue(items.none { it.exam != null })
    }
}
