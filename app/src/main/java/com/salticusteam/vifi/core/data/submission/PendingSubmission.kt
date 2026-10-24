package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSubmission
import com.salticusteam.vifi.core.model.SubmissionFile
import com.salticusteam.vifi.core.model.SubmissionTier
import java.security.SecureRandom
import kotlinx.serialization.Serializable

// Added in 3.1.0 — a submission queued for a background upload, stored next to its draft pages.
/**
 * Everything the upload worker needs besides the page bytes (which stay as files in the draft directory). Written
 * once per user-initiated submit, so every retry of that submit — after a lost connection or a killed process —
 * reuses the same [submissionId] and therefore the same Firestore document and Storage folder.
 */
@Serializable
data class PendingSubmission(
    /** The Firestore document id, reused as the Storage folder name (`pending/<uid>/<submissionId>/…`). */
    val submissionId: String,
    val ownerId: String,
    val university: String,
    val faculty: String,
    val department: String,
    val lesson: String,
    val examName: String,
    val year: Int,
    /** `ExamKind.raw` (`"JPG"` / `"PDF"`). */
    val kind: String,
    /** The page count the draft held when submitted; a mismatch means the draft lost pages. */
    val fileCount: Int,
    /** `SubmissionTier.raw` estimated when submitted, for the `exam_submit` analytics event; `null` when unknown. */
    val estimatedTier: String? = null,
) {
    val tier: SubmissionTier? get() = SubmissionTier.fromRaw(estimatedTier)

    /** The submission with [files] as its pages, or `null` when the stored kind is unknown. */
    fun submission(files: List<SubmissionFile>): ExamSubmission? {
        val examKind = ExamKind.fromRaw(kind) ?: return null
        return ExamSubmission(university, faculty, department, lesson, examName, year, examKind, files)
    }

    companion object {
        /** Builds the record for [submission] owned by [ownerId], under a fresh [submissionId]. */
        fun of(
            submission: ExamSubmission,
            ownerId: String,
            tier: SubmissionTier?,
            submissionId: String = newSubmissionId(),
        ) = PendingSubmission(
            submissionId = submissionId,
            ownerId = ownerId,
            university = submission.university,
            faculty = submission.faculty,
            department = submission.department,
            lesson = submission.lesson,
            examName = submission.examName,
            year = submission.year,
            kind = submission.kind.raw,
            fileCount = submission.files.size,
            estimatedTier = tier?.raw,
        )

        private const val ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private const val ID_LENGTH = 20
        private val random = SecureRandom()

        /** A 20-character alphanumeric id, the shape of a Firestore auto-id, generated without Firebase. */
        fun newSubmissionId(): String =
            buildString(ID_LENGTH) { repeat(ID_LENGTH) { append(ID_ALPHABET[random.nextInt(ID_ALPHABET.length)]) } }
    }
}
