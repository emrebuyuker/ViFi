package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.model.ExamSubmission
import com.salticusteam.vifi.core.model.fileExtension
import com.salticusteam.vifi.core.model.uploadContentType

/**
 * The server-ready form of an [ExamSubmission] (iOS `ExamSubmissionPayload`): the exact field values written to
 * Firestore `submissions/<submissionId>` (every key except the server-stamped `createdAt`) together with the
 * Cloud Storage uploads.
 *
 * Computed purely from an [ExamSubmission], so the year folding, file naming and storage paths are unit-tested
 * without Firebase; the live service only adds the server timestamp and performs the I/O. The field set and the
 * 1..50-file list match `firestore.rules` (`submissions` whitelist) and `storage.rules`
 * (`pending/<uid>/<submissionId>/<fileName>`).
 */
data class ExamSubmissionPayload(
    val ownerId: String,
    val university: String,
    val faculty: String,
    val department: String,
    val lesson: String,
    /** The exam name with the year folded in (the schema has no separate year field). */
    val exam: String,
    /** `"JPG"` or `"PDF"` — `ExamKind.raw`. */
    val kind: String,
    /** Always [PENDING_STATUS] at creation. */
    val status: String,
    /** The files to upload in ascending order; each carries its storage path, content type and bytes. */
    val files: List<UploadFile>,
) {
    /** The `files` array as the document stores it: `[{ storagePath, order }]`, ordered. */
    val fileDocuments: List<Map<String, Any>>
        get() = files.map { mapOf("storagePath" to it.storagePath, "order" to it.order.toLong()) }

    /**
     * The document's fields except the server-stamped `createdAt` — the nine non-timestamp keys of the ten-key
     * `submissions` schema.
     */
    val firestoreFields: Map<String, Any>
        get() = mapOf(
            "ownerId" to ownerId,
            "university" to university,
            "faculty" to faculty,
            "department" to department,
            "lesson" to lesson,
            "exam" to exam,
            "kind" to kind,
            "status" to status,
            "files" to fileDocuments,
        )

    companion object {
        /** The literal `status` a submission is created with; the only value the create rule accepts. */
        const val PENDING_STATUS = "pending"

        /** The server-stamped key the live service adds to [firestoreFields]. */
        const val CREATED_AT = "createdAt"

        /**
         * Builds the payload. [submissionId] is the Firestore document id, reused as the Storage folder name so
         * moderation can correlate the document with its files. Files are emitted in ascending `order`, each
         * named `<order>.<ext>`, so names are unique within the submission and never overwrite an object (the
         * Storage rules reject overwrites).
         */
        fun make(submission: ExamSubmission, ownerId: String, submissionId: String): ExamSubmissionPayload {
            val kind = submission.kind
            val files = submission.files.sortedBy { it.order }.map { file ->
                val fileName = "${file.order}.${kind.fileExtension}"
                UploadFile(
                    storagePath = "pending/$ownerId/$submissionId/$fileName",
                    order = file.order,
                    contentType = kind.uploadContentType,
                    data = file.data,
                )
            }
            return ExamSubmissionPayload(
                ownerId = ownerId,
                university = submission.university,
                faculty = submission.faculty,
                department = submission.department,
                lesson = submission.lesson,
                exam = ExamSubmission.examText(submission.examName, submission.year),
                kind = kind.raw,
                status = PENDING_STATUS,
                files = files,
            )
        }
    }
}

/** One Cloud Storage upload of a submission. Equality compares the bytes by content. */
class UploadFile(
    /** Full object path: `pending/<uid>/<submissionId>/<order>.<ext>`. */
    val storagePath: String,
    /** 1-based position in reading order. */
    val order: Int,
    /** `"image/jpeg"` or `"application/pdf"`, as the Storage rules require. */
    val contentType: String,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is UploadFile && other.storagePath == storagePath && other.order == order &&
            other.contentType == contentType && other.data.contentEquals(data)

    override fun hashCode(): Int = 31 * storagePath.hashCode() + data.contentHashCode()

    override fun toString(): String = "UploadFile(storagePath=$storagePath, bytes=${data.size})"
}
