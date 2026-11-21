package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.AIProvider
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
     * First approval (4.2.0, iOS `approve(_:provider:)`): copies the files into the public archive storage, credits
     * the owner the tier reward and marks the submission approved with the chosen AI [provider] and the publish plan;
     * the server converts the pages into draft questions and the admin publishes them at the second approval.
     *
     * Until ADM-01 the implementation still publishes directly (the 4.1.0 path) and ignores [provider].
     */
    suspend fun approve(record: ExamSubmissionRecord, provider: AIProvider): ApprovalResult

    /**
     * The id of the existing archive exam [record] duplicates, or `null` when it is new or the archive could not be
     * resolved (K22: drives the "Bu sınav arşivde zaten yüklü" warning). Never throws (cancellation aside).
     */
    suspend fun duplicateExamId(record: ExamSubmissionRecord): String?

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
