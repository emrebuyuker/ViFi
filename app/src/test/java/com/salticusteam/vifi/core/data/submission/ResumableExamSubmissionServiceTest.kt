package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSubmission
import com.salticusteam.vifi.core.model.ExamSubmissionError
import com.salticusteam.vifi.core.model.SubmissionFile
import com.salticusteam.vifi.fake.FakeSubmissionBackend
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ResumableExamSubmissionServiceTest {

    private val submission = ExamSubmission(
        university = "Ege Üniversitesi",
        faculty = "Mühendislik",
        department = "Bilgisayar",
        lesson = "BLG 101",
        examName = "Vize",
        year = 2024,
        kind = ExamKind.IMAGES,
        files = listOf(SubmissionFile(byteArrayOf(1, 2), 1), SubmissionFile(byteArrayOf(3), 2), SubmissionFile(byteArrayOf(4), 3)),
    )
    private val paths = (1..3).map { "pending/uid/sub1/$it.jpg" }

    private lateinit var backend: FakeSubmissionBackend
    private lateinit var sut: ResumableExamSubmissionService

    @Before
    fun setUp() {
        backend = FakeSubmissionBackend()
        sut = ResumableExamSubmissionService(backend)
    }

    private suspend fun submitExpectingFailure(): ExamSubmissionError = try {
        sut.submit(submission, "uid", "sub1")
        fail("Expected a failure")
        throw AssertionError()
    } catch (e: ExamSubmissionError) {
        e
    }

    @Test
    fun `submit uploads every file in order, then creates the document`() = runTest {
        sut.submit(submission, "uid", "sub1")

        assertEquals(paths, backend.uploadedPaths)
        val document = backend.documents.getValue("sub1")
        assertEquals("uid", document["ownerId"])
        assertEquals("2024 Vize", document["exam"])
        assertEquals("pending", document["status"])
    }

    @Test
    fun `submit when someone else is signed in uploads nothing`() = runTest {
        backend.currentUserId = "other"

        assertSame(ExamSubmissionError.NotSignedIn, submitExpectingFailure())
        assertTrue(backend.uploadedPaths.isEmpty())
    }

    @Test
    fun `submit after an interrupted upload resumes without uploading the stored files again`() = runTest {
        backend.uploadFailures[3] = ExamSubmissionError.Offline
        assertSame(ExamSubmissionError.Offline, submitExpectingFailure())
        assertEquals(paths.take(2), backend.uploadedPaths)

        sut.submit(submission, "uid", "sub1")

        // Each file went up exactly once and nothing was removed on the way.
        assertEquals(paths, backend.uploadedPaths)
        assertTrue(backend.deletedPaths.isEmpty())
        assertEquals(setOf("sub1"), backend.documents.keys)
    }

    @Test
    fun `submit replaces a stored object whose content differs`() = runTest {
        backend.objects[paths[0]] = byteArrayOf(9, 9)

        sut.submit(submission, "uid", "sub1")

        assertEquals(listOf(paths[0]), backend.deletedPaths)
        assertTrue(backend.objects.getValue(paths[0]).contentEquals(byteArrayOf(1, 2)))
    }

    @Test
    fun `submit after a lost create acknowledgement does not create a second document`() = runTest {
        backend.loseCreateAcknowledgement = true
        assertSame(ExamSubmissionError.Offline, submitExpectingFailure())

        sut.submit(submission, "uid", "sub1")

        assertEquals(2, backend.createCalls)
        assertEquals(1, backend.documents.size)
        assertEquals(3, backend.uploadedPaths.size)
    }

    @Test
    fun `submit when the create is denied and no document exists reports the denial`() = runTest {
        backend.createFailure = ExamSubmissionError.PermissionDenied

        assertSame(ExamSubmissionError.PermissionDenied, submitExpectingFailure())
        assertTrue(backend.documents.isEmpty())
    }

    @Test
    fun `discardUploads removes the files of a submission without a document`() = runTest {
        backend.uploadFailures[3] = ExamSubmissionError.Offline
        submitExpectingFailure()

        sut.discardUploads(submission, "uid", "sub1")

        assertEquals(paths, backend.deletedPaths)
        assertTrue(backend.objects.isEmpty())
    }

    @Test
    fun `discardUploads keeps the files of a created document`() = runTest {
        sut.submit(submission, "uid", "sub1")

        sut.discardUploads(submission, "uid", "sub1")

        assertTrue(backend.deletedPaths.isEmpty())
        assertEquals(3, backend.objects.size)
    }

    @Test
    fun `discardUploads keeps the files when the document cannot be checked`() = runTest {
        backend.uploadFailures[2] = ExamSubmissionError.Offline
        submitExpectingFailure()
        backend.existenceUnknown = true

        sut.discardUploads(submission, "uid", "sub1")

        assertTrue(backend.deletedPaths.isEmpty())
    }
}
