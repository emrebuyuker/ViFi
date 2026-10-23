package com.salticusteam.vifi.core.data.moderation

import com.salticusteam.vifi.core.data.repository.RewardsConfigReader
import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeRewardsConfigReader
import com.salticusteam.vifi.core.data.submission.SubmissionTierResolver
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSubmissionRecord
import com.salticusteam.vifi.core.model.ModerationError
import com.salticusteam.vifi.core.model.RewardsConfig
import com.salticusteam.vifi.core.model.SubmissionFileRef
import com.salticusteam.vifi.core.model.SubmissionTier
import com.salticusteam.vifi.fake.FakeModerationBackend
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Against FakeArchiveRepository's sample tree: İTÜ > Mühendislik > Bilgisayar Mühendisliği > BLG 101 > 2023 Vize. */
class AdminModerationServiceTest {

    private val backend = FakeModerationBackend()
    private val archive = FakeArchiveRepository()
    private var fileIds = 0

    private fun sut(
        rewards: RewardsConfigReader = FakeRewardsConfigReader(
            RewardsConfig(examUploadCoins = 10, tiers = FakeRewardsConfigReader.SAMPLE_TIERS),
        ),
    ) = AdminModerationService(
        backend = backend,
        resolver = SubmissionTierResolver(archive),
        rewards = rewards,
        newFileId = { "file-${++fileIds}" },
        rewardsTimeout = 5.seconds,
    )

    private fun record(
        id: String = "sub-1",
        university: String = "İSTANBUL TEKNİK ÜNİVERSİTESİ",
        exam: String = "2019 VİZE",
        kind: ExamKind = ExamKind.IMAGES,
        status: String = "pending",
        files: List<SubmissionFileRef> = listOf(
            SubmissionFileRef("pending/u1/$id/2.jpg", order = 2),
            SubmissionFileRef("pending/u1/$id/1.jpg", order = 1),
        ),
        createdAt: Instant? = null,
    ): ExamSubmissionRecord {
        val record = ExamSubmissionRecord(
            id = id,
            ownerId = "u1",
            university = university,
            faculty = "Mühendislik Fakültesi",
            department = "Bilgisayar Mühendisliği",
            lesson = "BLG 101",
            exam = exam,
            kind = kind,
            status = status,
            createdAt = createdAt,
            files = files,
        )
        backend.statuses[id] = status
        backend.files += files.map { it.storagePath }
        return record
    }

    // --- approve ---

    @Test
    fun `approve when new exam copies files in order and commits publish and credit`() = runTest {
        val record = record()

        val result = sut().approve(record)

        assertEquals(SubmissionTier.NEW_EXAM, result.tier)
        assertEquals(60, result.coins)
        assertEquals("blg101__2019-vize", result.publishedExamId)
        assertEquals(
            listOf("pending/u1/sub-1/1.jpg" to "imagess/file-1.jpg", "pending/u1/sub-1/2.jpg" to "imagess/file-2.jpg"),
            backend.copies,
        )
        val commit = backend.commits.single()
        assertEquals(60, commit.coins)
        assertEquals("u1", commit.ownerId)
        assertEquals(listOf("blg101"), commit.childCountIncrements)
        assertTrue(commit.nodesToCreate.isEmpty())
        assertEquals("blg101__2019-vize", commit.examId)
        assertEquals(
            listOf(
                mapOf("storagePath" to "imagess/file-1.jpg", "order" to 1),
                mapOf("storagePath" to "imagess/file-2.jpg", "order" to 2),
            ),
            commit.examDocument?.get("files"),
        )
        assertTrue(backend.deletedFiles.isEmpty())
    }

    @Test
    fun `approve when pdf copies into pdfs folder`() = runTest {
        val record = record(kind = ExamKind.PDF, files = listOf(SubmissionFileRef("pending/u1/sub-1/1.pdf", 1)))

        sut().approve(record)

        assertEquals(listOf("pending/u1/sub-1/1.pdf" to "pdfs/file-1.pdf"), backend.copies)
    }

    @Test
    fun `approve when duplicate copies nothing and only credits`() = runTest {
        val result = sut().approve(record(exam = "2023 VİZE"))

        assertEquals(SubmissionTier.DUPLICATE, result.tier)
        assertEquals(10, result.coins)
        assertNull(result.publishedExamId)
        assertTrue(backend.copies.isEmpty())
        val commit = backend.commits.single()
        assertNull(commit.examId)
        assertNull(commit.examDocument)
        assertTrue(commit.nodesToCreate.isEmpty())
        assertTrue(commit.childCountIncrements.isEmpty())
    }

    @Test
    fun `approve when new university creates the whole chain`() = runTest {
        val result = sut().approve(record(university = "Ege Üniversitesi"))

        assertEquals(SubmissionTier.NEW_UNIVERSITY, result.tier)
        assertEquals(500, result.coins)
        assertEquals(4, backend.commits.single().nodesToCreate.size)
    }

    @Test
    fun `approve when no reward configured publishes for zero coins without a ledger check`() = runTest {
        backend.ledger += "u1/sub-1" // would block a credit; irrelevant without one
        val result = sut(rewards = FakeRewardsConfigReader(null)).approve(record())

        assertEquals(0, result.coins)
        assertEquals(0, backend.commits.single().coins)
    }

    @Test
    fun `approve when rewards never answer fails offline instead of crediting zero`() = runTest {
        val silent = object : RewardsConfigReader {
            override fun rewardsChanges(): Flow<RewardsConfig?> = flow { awaitCancellation() }
        }

        assertThrows(ModerationError.Offline) { sut(rewards = silent).approve(record()) }
        assertTrue(backend.commits.isEmpty())
    }

    @Test
    fun `approve when record already handled throws notPending without touching the backend`() = runTest {
        assertThrows(ModerationError.NotPending) { sut().approve(record(status = "approved")) }
        assertTrue(backend.copies.isEmpty())
        assertTrue(backend.commits.isEmpty())
    }

    @Test
    fun `approve when another admin approved meanwhile throws notPending and removes the copies`() = runTest {
        val record = record()
        backend.statuses[record.id] = "approved" // changed on the server after the list was loaded

        assertThrows(ModerationError.NotPending) { sut().approve(record) }
        assertTrue(backend.commits.isEmpty())
        assertEquals(listOf("imagess/file-1.jpg", "imagess/file-2.jpg"), backend.deletedFiles)
    }

    @Test
    fun `approve when ledger entry exists throws alreadyCredited`() = runTest {
        val record = record()
        backend.ledger += "u1/sub-1"

        assertThrows(ModerationError.AlreadyCredited) { sut().approve(record) }
        assertTrue(backend.commits.isEmpty())
    }

    @Test
    fun `approve twice credits once`() = runTest {
        val sut = sut()
        val record = record()

        sut.approve(record)
        // A stale copy of the record (still "pending" locally) tapped again.
        assertThrows(ModerationError.NotPending) { sut.approve(record) }

        assertEquals(1, backend.commits.size)
    }

    @Test
    fun `approve when commit fails removes the copied files`() = runTest {
        backend.commitFailure = ModerationError.Offline

        assertThrows(ModerationError.Offline) { sut().approve(record()) }
        assertEquals(listOf("imagess/file-1.jpg", "imagess/file-2.jpg"), backend.deletedFiles)
    }

    @Test
    fun `approve when a later copy fails removes the earlier ones`() = runTest {
        val files = listOf(SubmissionFileRef("pending/u1/sub-1/1.jpg", 1), SubmissionFileRef("gone.jpg", 2))
        val record = record(files = files)
        backend.files -= "gone.jpg"

        try {
            sut().approve(record)
            fail("expected a failure")
        } catch (e: ModerationError.Unknown) {
            // expected
        }
        assertEquals(listOf("imagess/file-1.jpg"), backend.deletedFiles)
    }

    @Test
    fun `approve when archive read is offline maps to moderation offline`() = runTest {
        archive.error = ArchiveError.Offline

        assertThrows(ModerationError.Offline) { sut().approve(record()) }
    }

    // --- reject ---

    @Test
    fun `reject trims the reason and blank means none`() = runTest {
        sut().reject(record(id = "a"), "  bulanık  ")
        sut().reject(record(id = "b"), "   ")

        assertEquals(listOf("a" to "bulanık", "b" to null), backend.rejections)
    }

    @Test
    fun `reject clamps a long reason to 120 characters`() = runTest {
        sut().reject(record(), "x".repeat(200))

        assertEquals(120, backend.rejections.single().second?.length)
    }

    @Test
    fun `reject when already handled on the server throws notPending`() = runTest {
        val record = record()
        backend.statuses[record.id] = "approved"

        assertThrows(ModerationError.NotPending) { sut().reject(record, null) }
        assertTrue(backend.rejections.isEmpty())
    }

    // --- deleteExam ---

    private val examPath = ArchivePath(listOf("itu", "muh", "blg", "blg101", "2023-vize"))

    @Test
    fun `deleteExam deletes the exam under its listed course and then its files`() = runTest {
        backend.publishedExams["2023-vize"] = PublishedExamRef(courseId = "other", storagePaths = listOf("pdfs/a.pdf"))

        sut().deleteExam(examPath)

        assertEquals(listOf("2023-vize" to "blg101"), backend.deletedExams)
        assertEquals(listOf("pdfs/a.pdf"), backend.deletedFiles)
    }

    @Test
    fun `deleteExam without a parent falls back to the document course`() = runTest {
        backend.publishedExams["x"] = PublishedExamRef(courseId = "c1", storagePaths = emptyList())

        // A single-component path's parent is the root (no components), so the document's course is used.
        sut().deleteExam(ArchivePath(listOf("x")))

        assertEquals(listOf("x" to "c1"), backend.deletedExams)
    }

    @Test
    fun `deleteExam when the exam is gone throws without decrementing`() = runTest {
        try {
            sut().deleteExam(examPath)
            fail("expected a failure")
        } catch (e: ModerationError.Unknown) {
            assertEquals("Silinecek sınav bulunamadı.", e.userMessage)
        }
        assertTrue(backend.deletedExams.isEmpty())
    }

    @Test
    fun `deleteExam ignores file cleanup failures`() = runTest {
        backend.publishedExams["2023-vize"] = PublishedExamRef(courseId = null, storagePaths = listOf("pdfs/a.pdf"))
        backend.deleteFileFailure = ModerationError.PermissionDenied

        sut().deleteExam(examPath)

        assertEquals(1, backend.deletedExams.size)
    }

    // --- queue & preview ---

    @Test
    fun `pendingSubmissions sorts newest first with undated last`() = runTest {
        backend.pending = listOf(
            record(id = "old", createdAt = Instant.ofEpochSecond(1)),
            record(id = "none"),
            record(id = "new", createdAt = Instant.ofEpochSecond(2)),
        )

        assertEquals(listOf("new", "old", "none"), sut().pendingSubmissions().map { it.id })
    }

    @Test
    fun `fileUrls follow the reading order`() {
        val urls = sut().fileUrls(record())

        assertEquals(
            listOf("https://files.test/pending/u1/sub-1/1.jpg", "https://files.test/pending/u1/sub-1/2.jpg"),
            urls,
        )
    }

    @Test
    fun `moderationError maps archive errors`() {
        assertSame(ModerationError.Offline, AdminModerationService.moderationError(ArchiveError.Offline))
        assertSame(ModerationError.PermissionDenied, AdminModerationService.moderationError(ArchiveError.PermissionDenied))
        assertFalse(AdminModerationService.moderationError(IllegalStateException()) is ModerationError.Offline)
    }

    private suspend fun assertThrows(expected: ModerationError, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $expected")
        } catch (e: ModerationError) {
            assertSame(expected, e)
        }
    }
}
