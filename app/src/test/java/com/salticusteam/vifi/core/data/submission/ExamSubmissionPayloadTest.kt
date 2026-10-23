package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSubmission
import com.salticusteam.vifi.core.model.SubmissionFile
import org.junit.Assert.assertEquals
import org.junit.Test

class ExamSubmissionPayloadTest {

    private fun submission(kind: ExamKind, files: List<SubmissionFile>) = ExamSubmission(
        university = "Boğaziçi Üniversitesi",
        faculty = "Mühendislik Fakültesi",
        department = "Bilgisayar Mühendisliği",
        lesson = "BLG 101",
        examName = "VİZE",
        year = 2023,
        kind = kind,
        files = files,
    )

    @Test
    fun `make names images by order under the pending folder`() {
        val payload = ExamSubmissionPayload.make(
            submission(ExamKind.IMAGES, listOf(SubmissionFile(byteArrayOf(2), 2), SubmissionFile(byteArrayOf(1), 1))),
            ownerId = "uid1",
            submissionId = "sub9",
        )

        assertEquals(listOf("pending/uid1/sub9/1.jpg", "pending/uid1/sub9/2.jpg"), payload.files.map { it.storagePath })
        assertEquals(listOf(1, 2), payload.files.map { it.order })
        assertEquals(setOf("image/jpeg"), payload.files.map { it.contentType }.toSet())
    }

    @Test
    fun `make for a pdf uses the pdf extension and content type`() {
        val payload = ExamSubmissionPayload.make(
            submission(ExamKind.PDF, listOf(SubmissionFile(byteArrayOf(1), 1))),
            ownerId = "uid1",
            submissionId = "sub9",
        )

        assertEquals("pending/uid1/sub9/1.pdf", payload.files.single().storagePath)
        assertEquals("application/pdf", payload.files.single().contentType)
        assertEquals("PDF", payload.kind)
    }

    @Test
    fun `firestoreFields holds the nine non-timestamp keys with the year folded in`() {
        val payload = ExamSubmissionPayload.make(
            submission(ExamKind.IMAGES, listOf(SubmissionFile(byteArrayOf(1), 1))),
            ownerId = "uid1",
            submissionId = "sub9",
        )
        val fields = payload.firestoreFields

        assertEquals(
            setOf("ownerId", "university", "faculty", "department", "lesson", "exam", "kind", "status", "files"),
            fields.keys,
        )
        assertEquals("uid1", fields["ownerId"])
        assertEquals("2023 VİZE", fields["exam"])
        assertEquals("JPG", fields["kind"])
        assertEquals("pending", fields["status"])
        assertEquals(listOf(mapOf("storagePath" to "pending/uid1/sub9/1.jpg", "order" to 1L)), fields["files"])
    }
}
