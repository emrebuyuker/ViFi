package com.salticusteam.vifi.core.model

import java.time.Instant

// Added in 2.5 — a submission as stored in Firestore `submissions/<id>` and read back by the moderation tools.
/**
 * The counterpart of the write-side [ExamSubmission]: the archive path is flat denormalized text, the exam name
 * and year are already folded into the single [exam] text, and each file is a reference to a `pending/<uid>/...`
 * object rather than raw bytes.
 */
data class ExamSubmissionRecord(
    val id: String,
    val ownerId: String,
    val university: String,
    val faculty: String,
    val department: String,
    val lesson: String,
    /** The exam's display text with the year folded in, e.g. "2023 VİZE". */
    val exam: String,
    val kind: ExamKind,
    /** The moderation state: `pending`, `approved` or `rejected`. */
    val status: String,
    val createdAt: Instant?,
    val files: List<SubmissionFileRef>,
) {
    /** The taxonomy location used to resolve the submission against the archive. */
    val location: SubmissionLocation
        get() = SubmissionLocation(
            university = university,
            faculty = faculty,
            department = department,
            lesson = lesson,
            examText = exam,
        )
}

// Added in 2.5 — a reference to one stored file of a submission: its Storage object path and reading-order position.
data class SubmissionFileRef(
    val storagePath: String,
    val order: Int,
)

// Added in 2.5 — a submission's place in the archive taxonomy, as flat display names.
data class SubmissionLocation(
    val university: String,
    val faculty: String,
    val department: String,
    val lesson: String,
    /** The exam's display text with the year folded in, e.g. "2023 VİZE": matches what the archive stores. */
    val examText: String,
) {
    /** Builds a location from the add-exam form's separate exam name and year. */
    constructor(
        university: String,
        faculty: String,
        department: String,
        lesson: String,
        examName: String,
        year: Int,
    ) : this(university, faculty, department, lesson, ExamSubmission.examText(examName, year))
}

// Added in 2.5 — turns raw `submissions/<id>` documents into records.
/** Pure and Firestore-free (the `createdAt` server timestamp is converted by the caller), tested with plain maps. */
object SubmissionRecordParser {

    /** The record for [id]/[data], or `null` when a required field is missing or malformed. */
    fun record(id: String, data: Map<String, Any?>, createdAt: Instant?): ExamSubmissionRecord? {
        val ownerId = data["ownerId"] as? String ?: return null
        val university = text(data["university"]) ?: return null
        val faculty = text(data["faculty"]) ?: return null
        val department = text(data["department"]) ?: return null
        val lesson = text(data["lesson"]) ?: return null
        val exam = text(data["exam"]) ?: return null
        val kind = ExamKind.fromRaw(data["kind"] as? String) ?: return null
        val status = (data["status"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        return ExamSubmissionRecord(
            id = id,
            ownerId = ownerId,
            university = university,
            faculty = faculty,
            department = department,
            lesson = lesson,
            exam = exam,
            kind = kind,
            status = status,
            createdAt = createdAt,
            files = files(data["files"]),
        )
    }

    /** The file references with a non-empty `storagePath`, in `order` (stable for equal/absent orders). */
    private fun files(value: Any?): List<SubmissionFileRef> {
        val entries = (value as? List<*>).orEmpty()
        return entries
            .mapIndexedNotNull { offset, raw ->
                val entry = raw as? Map<*, *> ?: return@mapIndexedNotNull null
                val path = text(entry["storagePath"]) ?: return@mapIndexedNotNull null
                // Firestore returns numbers as Long on Android, so accept any Number.
                val order = (entry["order"] as? Number)?.toInt() ?: offset
                offset to SubmissionFileRef(storagePath = path, order = order)
            }
            .sortedWith(compareBy({ it.second.order }, { it.first }))
            .map { it.second }
    }

    /** A non-empty string, or `null`. */
    private fun text(value: Any?): String? = (value as? String)?.takeIf { it.isNotEmpty() }
}
