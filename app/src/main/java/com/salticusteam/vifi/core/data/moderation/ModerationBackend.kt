package com.salticusteam.vifi.core.data.moderation

import com.salticusteam.vifi.core.data.submission.CatalogNodeWrite
import com.salticusteam.vifi.core.model.ExamSubmissionRecord
import com.salticusteam.vifi.core.model.SubmissionTier

/**
 * The raw backend operations moderation is made of (Firestore + Storage on the live build). Every call throws
 * `ModerationError` (or `CancellationException`); the decisions — idempotency, which files to copy, what to
 * credit — live in [AdminModerationService], so they are unit-tested against a fake backend.
 */
interface ModerationBackend {
    /** Every `submissions/<id>` document with `status == pending` that parses, in no particular order. */
    suspend fun pendingSubmissions(): List<ExamSubmissionRecord>

    /** The submission's current `status`, read from the server (never the cache); `null` when it is gone. */
    suspend fun submissionStatus(submissionId: String): String?

    /** Whether `wallets/<ownerId>/ledger/<submissionId>` exists, read from the server (never the cache). */
    suspend fun ledgerEntryExists(ownerId: String, submissionId: String): Boolean

    /**
     * Downloads the object at [sourcePath] (at most [maxBytes]) and uploads its bytes to [destinationPath] with
     * fresh metadata carrying only [contentType] — a pending file's own metadata never reaches the archive.
     */
    suspend fun copyFile(sourcePath: String, destinationPath: String, contentType: String, maxBytes: Long)

    /** Deletes a Storage object. */
    suspend fun deleteFile(storagePath: String)

    /** Writes the whole approval in one atomic batch (see [ApprovalCommit]). */
    suspend fun commitApproval(commit: ApprovalCommit)

    /** Marks the submission rejected (with `reviewedAt`), recording [reason] when given. */
    suspend fun reject(submissionId: String, reason: String?)

    /** The published exam's course id and file paths, or `null` when `exams/<examId>` does not exist. */
    suspend fun publishedExam(examId: String): PublishedExamRef?

    /** Deletes `exams/<examId>` and decrements `catalogNodes/<courseId>.childCount`, atomically. */
    suspend fun deleteExam(examId: String, courseId: String)

    /** The download URL of a Storage object (the file loader adds the credentials), or `null` when unknown. */
    fun fileUrl(storagePath: String): String?
}

// Added in 2.5 — everything one approval writes, committed atomically by [ModerationBackend.commitApproval].
/**
 * The catalog nodes and child-count bumps, the exam document (when a new exam is published), the coin credit
 * (when [coins] > 0: `wallets/<owner>.balance += coins` plus the `ledger/<submissionId>` earn entry) and the
 * submission's status change to `approved`. Server timestamps are added by the backend.
 */
data class ApprovalCommit(
    val submissionId: String,
    val ownerId: String,
    val tier: SubmissionTier,
    val coins: Int,
    val nodesToCreate: List<CatalogNodeWrite>,
    val childCountIncrements: List<String>,
    /** The exam to create, or `null` for a duplicate. */
    val examId: String?,
    /** The `exams/<examId>` fields; `null` exactly when [examId] is. */
    val examDocument: Map<String, Any>?,
)

// Added in 2.5 — what an admin deletion needs from a published `exams/<id>` document.
data class PublishedExamRef(
    /** The exam's course (`courseId`), whose `childCount` drops; `null` when the document lacks it. */
    val courseId: String?,
    val storagePaths: List<String>,
)
