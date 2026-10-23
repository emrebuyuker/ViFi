package com.salticusteam.vifi.fake

import com.salticusteam.vifi.core.data.moderation.ApprovalCommit
import com.salticusteam.vifi.core.data.moderation.ModerationBackend
import com.salticusteam.vifi.core.data.moderation.PublishedExamRef
import com.salticusteam.vifi.core.model.ExamSubmissionRecord
import com.salticusteam.vifi.core.model.ModerationError

/**
 * In-memory moderation backend: submission statuses, ledger entries, Storage objects and published exams are
 * plain maps, so the service's idempotency and race handling are tested without Firebase.
 */
class FakeModerationBackend : ModerationBackend {

    var pending: List<ExamSubmissionRecord> = emptyList()

    /** `submissions/<id>.status` as the server has it; a missing id reads as gone (`null`). */
    val statuses = mutableMapOf<String, String>()

    /** Existing `wallets/<owner>/ledger/<submissionId>` entries, as "owner/submissionId". */
    val ledger = mutableSetOf<String>()

    /** Storage objects by path. */
    val files = mutableSetOf<String>()

    val publishedExams = mutableMapOf<String, PublishedExamRef>()

    // Recorded calls
    val copies = mutableListOf<Pair<String, String>>()
    val deletedFiles = mutableListOf<String>()
    val commits = mutableListOf<ApprovalCommit>()
    val rejections = mutableListOf<Pair<String, String?>>()
    val deletedExams = mutableListOf<Pair<String, String>>()

    // Failure injection
    var commitFailure: ModerationError? = null
    var copyFailure: ModerationError? = null
    var deleteFileFailure: ModerationError? = null

    /** Runs right before the commit is applied (e.g. another admin acting in between). */
    var beforeCommit: () -> Unit = {}

    /** Runs right before a status read (e.g. another admin approving while this one resolves). */
    var beforeStatusRead: () -> Unit = {}

    override suspend fun pendingSubmissions(): List<ExamSubmissionRecord> = pending

    override suspend fun submissionStatus(submissionId: String): String? {
        beforeStatusRead()
        return statuses[submissionId]
    }

    override suspend fun ledgerEntryExists(ownerId: String, submissionId: String): Boolean =
        "$ownerId/$submissionId" in ledger

    override suspend fun copyFile(sourcePath: String, destinationPath: String, contentType: String, maxBytes: Long) {
        copyFailure?.let { throw it }
        if (sourcePath !in files) throw ModerationError.Unknown("missing $sourcePath")
        files += destinationPath
        copies += sourcePath to destinationPath
    }

    override suspend fun deleteFile(storagePath: String) {
        deleteFileFailure?.let { throw it }
        files -= storagePath
        deletedFiles += storagePath
    }

    override suspend fun commitApproval(commit: ApprovalCommit) {
        beforeCommit()
        commitFailure?.let { throw it }
        commits += commit
        statuses[commit.submissionId] = "approved"
        if (commit.coins > 0) ledger += "${commit.ownerId}/${commit.submissionId}"
    }

    override suspend fun reject(submissionId: String, reason: String?) {
        rejections += submissionId to reason
        statuses[submissionId] = "rejected"
    }

    override suspend fun publishedExam(examId: String): PublishedExamRef? = publishedExams[examId]

    override suspend fun deleteExam(examId: String, courseId: String) {
        publishedExams -= examId
        deletedExams += examId to courseId
    }

    override fun fileUrl(storagePath: String): String = "https://files.test/$storagePath"
}
