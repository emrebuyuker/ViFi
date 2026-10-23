package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.ApprovalResult
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamSubmissionRecord

/**
 * Admin-only moderation of user submissions: list the queue, approve (publish + credit) or reject. Throws
 * `ModerationError` on failure.
 *
 * Serverless: an admin's device performs the writes, gated by the `isAdmin()` security rules. Approving is
 * idempotent: the ledger entry is keyed by the submission id and the submission must still be `pending`.
 */
interface ModerationService {
    /** The submissions awaiting review, newest first. */
    suspend fun pendingSubmissions(): List<ExamSubmissionRecord>

    /**
     * Approves a submission: publishes it into the archive (creating any missing catalog nodes and copying the
     * files into the public archive storage) and credits the owner the tier reward.
     */
    suspend fun approve(record: ExamSubmissionRecord): ApprovalResult

    /** Rejects a submission, with an optional [reason] recorded on it. */
    suspend fun reject(record: ExamSubmissionRecord, reason: String?)

    /**
     * Deletes a published exam from the archive: removes its `exams` document, decrements its course's child
     * count, and best-effort deletes the Storage files. [path] is the exam's archive path (.../course/exam).
     */
    suspend fun deleteExam(path: ArchivePath)

    /** Download URLs of the submission's pending files in reading order, for the admin preview (no I/O). */
    fun fileUrls(record: ExamSubmissionRecord): List<String>
}
