package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PracticeQuestion
import com.salticusteam.vifi.core.model.PracticeRequest
import com.salticusteam.vifi.core.model.PracticeScope
import com.salticusteam.vifi.core.model.PracticeSelection
import com.salticusteam.vifi.core.navigation.ViFiRoute
import com.salticusteam.vifi.core.util.ExamRouting
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 4.2.0 mock sample data (SCT-01): the structured exam, its subject, the pool index and the free daily limit. */
class StructuredSampleDataTest {

    private val lesson = ArchivePath(
        listOf("kocaeli-universitesi", "kocaeli-muhendislik", "kocaeli-muhendislik-matematik", FakePoolIndexReader.SAMPLE_COURSE_ID),
    )

    @Test
    fun `the structured sample lists as a structured exam and routes to the question screen`() = runTest {
        val repository = FakeArchiveRepository(includeStructuredSample = true)

        val exam = repository.items(lesson).single()

        assertEquals("2003 FİNAL", exam.name)
        assertEquals(3, exam.exam?.questionCount)
        assertTrue(ExamRouting.route(exam) is ViFiRoute.StructuredExam)
        assertEquals(3, repository.document(exam.path).questionCount)
    }

    @Test
    fun `the default subject lookup finds the course among its parent's entries`() = runTest {
        val repository = FakeArchiveRepository(includeStructuredSample = true)

        assertEquals(FakePoolIndexReader.SAMPLE_SUBJECT_ID, repository.subjectId(lesson))
        assertNull(repository.subjectId(ArchivePath(listOf("itu", "muh", "blg", "blg101"))))
        assertNull(FakeArchiveRepository().subjectId(lesson))
    }

    @Test
    fun `the default tree is unchanged without the structured sample`() = runTest {
        assertEquals(2, FakeArchiveRepository().items(ArchivePath.ROOT).size)
        assertEquals(3, FakeArchiveRepository(includeStructuredSample = true).items(ArchivePath.ROOT).size)
    }

    @Test
    fun `the sample index offers final in every scope for the mock course and profile`() = runTest {
        val index = requireNotNull(FakePoolIndexReader().index(FakePoolIndexReader.SAMPLE_SUBJECT_ID))
        val course = FakePoolIndexReader.SAMPLE_COURSE_ID
        val university = FakePoolIndexReader.SAMPLE_UNIVERSITY_ID

        assertEquals(3, index.count(ExamType.FINAL, PracticeScope.COURSE, course, university))
        assertEquals(2, index.count(ExamType.FINAL, PracticeScope.UNIVERSITY, course, university))
        assertEquals(1, index.count(ExamType.VIZE, PracticeScope.TURKEY, course, university))
    }

    @Test
    fun `the fake practice service stops a free user after five questions`() = runTest {
        val pool = (1..7).map { order ->
            PracticeQuestion(
                SampleQuestions.questions("e").first().copy(id = "e-%03d".format(order), order = order),
                examTitle = "2003 FİNAL",
                universityName = null,
            )
        }
        val service = FakePracticeService(pool = pool)
        val selection = PracticeSelection("s", ExamType.FINAL, PracticeScope.TURKEY, "c", null, 7)
        val seen = mutableListOf<String>()

        repeat(5) {
            val batch = service.nextQuestions(PracticeRequest(selection, seen.toList(), count = 1))
            seen += batch.questions.single().id
        }
        val blocked = service.nextQuestions(PracticeRequest(selection, seen.toList(), count = 1))

        assertTrue(blocked.limitReached)
        assertTrue(blocked.questions.isEmpty())
        assertEquals(0, blocked.remainingToday)
    }
}
