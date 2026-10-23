package com.salticusteam.vifi.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ExamSubmissionTest {

    private fun file(order: Int = 1, size: Int = 10) = SubmissionFile(ByteArray(size) { 1 }, order)

    private fun submission(
        kind: ExamKind = ExamKind.IMAGES,
        examName: String = "Vize",
        year: Int = 2023,
        files: List<SubmissionFile> = listOf(file()),
        university: String = "İTÜ",
    ) = ExamSubmission(university, "Mühendislik", "Bilgisayar", "BLG 101", examName, year, kind, files)

    @Test
    fun `examText when name and year folds year first`() {
        assertEquals("2023 Vize", ExamSubmission.examText("Vize", 2023))
    }

    @Test
    fun `validationError when submission is valid returns null`() {
        assertNull(submission().validationError)
        assertTrue(submission().isValid)
    }

    @Test
    fun `validationError when text empty or too long returns InvalidText`() {
        assertEquals(ExamSubmissionValidationError.InvalidText, submission(examName = "").validationError)
        assertEquals(ExamSubmissionValidationError.InvalidText, submission(university = "").validationError)
        assertEquals(ExamSubmissionValidationError.InvalidText, submission(university = "x".repeat(121)).validationError)
        assertNull(submission(university = "x".repeat(120)).validationError)
    }

    @Test
    fun `validationError when year out of range returns InvalidYear`() {
        assertEquals(ExamSubmissionValidationError.InvalidYear, submission(year = 1979).validationError)
        assertEquals(ExamSubmissionValidationError.InvalidYear, submission(year = 2101).validationError)
    }

    @Test
    fun `validationError when file count is wrong returns matching error`() {
        assertEquals(ExamSubmissionValidationError.NoFiles, submission(files = emptyList()).validationError)
        val tooMany = (1..51).map { file(it) }
        assertEquals(ExamSubmissionValidationError.TooManyFiles, submission(files = tooMany).validationError)
        assertEquals(
            ExamSubmissionValidationError.PdfMustBeSingleFile,
            submission(kind = ExamKind.PDF, files = listOf(file(1), file(2))).validationError,
        )
    }

    @Test
    fun `validationError when file empty or oversized returns its order`() {
        assertEquals(
            ExamSubmissionValidationError.EmptyFile(2),
            submission(files = listOf(file(1), file(2, size = 0))).validationError,
        )
        assertEquals(
            ExamSubmissionValidationError.FileTooLarge(1),
            submission(files = listOf(file(1, size = ExamSubmission.MAX_FILE_BYTES + 1))).validationError,
        )
    }

    @Test
    fun `submission file equality compares bytes by content`() {
        assertEquals(file(), file())
        assertFalse(file(order = 1) == file(order = 2))
    }

    @Test
    fun `kind upload metadata matches storage rules`() {
        assertEquals("image/jpeg", ExamKind.IMAGES.uploadContentType)
        assertEquals("pdf", ExamKind.PDF.fileExtension)
    }

    @Test
    fun `record parser when valid builds record with files sorted by order`() {
        val data = mapOf(
            "ownerId" to "u1", "university" to "İTÜ", "faculty" to "Mühendislik", "department" to "Bilgisayar",
            "lesson" to "BLG 101", "exam" to "2023 VİZE", "kind" to "JPG", "status" to "pending",
            "files" to listOf(
                mapOf("storagePath" to "pending/u1/s/b.jpg", "order" to 2L),
                mapOf("storagePath" to "pending/u1/s/a.jpg", "order" to 1L),
                mapOf("storagePath" to "", "order" to 3L),
                mapOf("storagePath" to "pending/u1/s/c.jpg"),
            ),
        )

        val record = SubmissionRecordParser.record("s1", data, Instant.EPOCH)
        assertNotNull(record)
        record ?: return

        assertEquals(ExamKind.IMAGES, record.kind)
        assertEquals(
            listOf("pending/u1/s/a.jpg", "pending/u1/s/b.jpg", "pending/u1/s/c.jpg"),
            record.files.map { it.storagePath },
        )
        assertEquals("2023 VİZE", record.location.examText)
    }

    @Test
    fun `record parser when required field missing returns null`() {
        val data = mapOf("ownerId" to "u1", "university" to "İTÜ")

        assertNull(SubmissionRecordParser.record("s1", data, null))
    }
}
