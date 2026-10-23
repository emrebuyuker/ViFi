package com.salticusteam.vifi.core.data.moderation

import com.salticusteam.vifi.core.data.repository.ModerationService
import com.salticusteam.vifi.core.data.repository.RewardsConfigReader
import com.salticusteam.vifi.core.data.submission.ArchivePublishPlan
import com.salticusteam.vifi.core.data.submission.SubmissionTierResolver
import com.salticusteam.vifi.core.model.ApprovalResult
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSubmission
import com.salticusteam.vifi.core.model.ExamSubmissionRecord
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import com.salticusteam.vifi.core.model.ModerationError
import com.salticusteam.vifi.core.model.RewardsConfig
import com.salticusteam.vifi.core.model.SubmissionFileRef
import com.salticusteam.vifi.core.model.fileExtension
import com.salticusteam.vifi.core.model.uploadContentType
import com.salticusteam.vifi.core.util.LogCategory
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Serverless admin moderation (iOS `FirebaseModerationService`), Firebase-free over a [ModerationBackend]: the
 * admin's device resolves the submission against the archive, copies its files into the public archive storage,
 * and commits the catalog nodes, the exam document, the coin credit and the status change in one atomic batch.
 *
 * Approving is idempotent: right before the commit the submission must still be `pending` on the server and,
 * when crediting, its ledger entry (keyed by the submission id) must not exist yet — so a stale list, a double
 * tap or a second admin cannot double-credit. Files copied for a commit that then fails are removed best-effort
 * (an Android addition; iOS leaves the orphans).
 */
class AdminModerationService internal constructor(
    private val backend: ModerationBackend,
    private val resolver: SubmissionTierResolver,
    private val rewards: RewardsConfigReader,
    private val newFileId: () -> String,
    private val rewardsTimeout: Duration,
) : ModerationService {

    @Inject
    constructor(
        backend: ModerationBackend,
        resolver: SubmissionTierResolver,
        rewards: RewardsConfigReader,
    ) : this(backend, resolver, rewards, newFileId = { UUID.randomUUID().toString() }, rewardsTimeout = 15.seconds)

    override suspend fun pendingSubmissions(): List<ExamSubmissionRecord> =
        backend.pendingSubmissions().sortedWith(NEWEST_FIRST)

    override suspend fun approve(record: ExamSubmissionRecord): ApprovalResult {
        if (record.status != PENDING) throw ModerationError.NotPending

        val placement = try {
            resolver.placement(record.location)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw moderationError(e)
        }
        val coins = (currentRewards()?.coins(placement.tier) ?: 0).coerceAtLeast(0)
        val plan = ArchivePublishPlan.make(record, placement)

        // Files are copied into the public archive storage only when a new exam is actually published.
        val copied = mutableListOf<SubmissionFileRef>()
        try {
            if (plan.examId != null) copyFiles(record, into = copied)
            ensureStillCreditable(record, coins)
            backend.commitApproval(
                ApprovalCommit(
                    submissionId = record.id,
                    ownerId = record.ownerId,
                    tier = placement.tier,
                    coins = coins,
                    nodesToCreate = plan.nodesToCreate,
                    childCountIncrements = plan.childCountIncrements,
                    examId = plan.examId,
                    examDocument = plan.examId?.let { plan.examDocument(record, copied) },
                ),
            )
        } catch (e: Exception) {
            // Nothing references the copies unless the batch committed; also on cancellation.
            withContext(NonCancellable) { removeCopies(copied) }
            throw e
        }
        return ApprovalResult(tier = placement.tier, coins = coins, publishedExamId = plan.examId)
    }

    override suspend fun reject(record: ExamSubmissionRecord, reason: String?) {
        if (record.status != PENDING) throw ModerationError.NotPending
        // Re-read like approve does, so a stale list cannot flip a submission another admin already approved.
        if (backend.submissionStatus(record.id) != PENDING) throw ModerationError.NotPending
        val trimmed = reason?.trim()?.takeIf { it.isNotEmpty() }?.let(::clampedReason)
        backend.reject(record.id, trimmed)
    }

    override suspend fun deleteExam(path: ArchivePath) {
        val examId = path.components.lastOrNull() ?: throw EXAM_NOT_FOUND
        val exam = backend.publishedExam(examId) ?: throw EXAM_NOT_FOUND
        // The listing's parent is the course; the document's own courseId covers a path without one.
        val courseId = path.parent?.components?.lastOrNull() ?: exam.courseId ?: throw EXAM_NOT_FOUND
        backend.deleteExam(examId, courseId)
        // Best-effort: the archive no longer references these files; a failure just leaves an orphan.
        for (storagePath in exam.storagePaths) {
            try {
                backend.deleteFile(storagePath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogCategory.FILES.log.w(e, "Could not remove a deleted exam's file")
            }
        }
    }

    override fun fileUrls(record: ExamSubmissionRecord): List<String> =
        record.files.sortedBy { it.order }.mapNotNull { backend.fileUrl(it.storagePath) }

    // Helpers

    /**
     * The current reward config: the first value of the live stream (`null` when nothing is configured, which
     * credits 0 coins). A stream that never answers fails as offline instead of silently approving for 0 coins.
     */
    private suspend fun currentRewards(): RewardsConfig? =
        try {
            withTimeout(rewardsTimeout) { rewards.rewardsChanges().first() }
        } catch (e: TimeoutCancellationException) {
            throw ModerationError.Offline
        }

    /** Re-checks right before committing that the submission is still pending and not already credited. */
    private suspend fun ensureStillCreditable(record: ExamSubmissionRecord, coins: Int) {
        if (backend.submissionStatus(record.id) != PENDING) throw ModerationError.NotPending
        if (coins > 0 && backend.ledgerEntryExists(record.ownerId, record.id)) throw ModerationError.AlreadyCredited
    }

    /** Copies each pending file, one at a time (bounded memory), under a fresh name; keeps the reading order. */
    private suspend fun copyFiles(record: ExamSubmissionRecord, into: MutableList<SubmissionFileRef>) {
        val folder = record.kind.archiveFolder
        for (file in record.files.sortedBy { it.order }) {
            val destination = "$folder/${newFileId()}.${record.kind.fileExtension}"
            backend.copyFile(
                sourcePath = file.storagePath,
                destinationPath = destination,
                contentType = record.kind.uploadContentType,
                maxBytes = ExamSubmission.MAX_FILE_BYTES.toLong(),
            )
            into += SubmissionFileRef(storagePath = destination, order = file.order)
        }
    }

    private suspend fun removeCopies(copies: List<SubmissionFileRef>) {
        for (copy in copies) {
            try {
                backend.deleteFile(copy.storagePath)
            } catch (e: Exception) {
                LogCategory.FILES.log.w(e, "Could not remove an orphaned archive copy")
            }
        }
    }

    internal companion object {
        const val PENDING = "pending"

        private val EXAM_NOT_FOUND = ModerationError.Unknown("Silinecek sınav bulunamadı.")

        /** Newest first; a submission without a timestamp sorts last. */
        private val NEWEST_FIRST = compareByDescending<ExamSubmissionRecord, Instant?>(nullsFirst()) {
            it.createdAt
        }

        /** The reason as stored: at most [ExamSubmission.MAX_TEXT_LENGTH] characters (whole code points). */
        fun clampedReason(reason: String): String {
            val max = ExamSubmission.MAX_TEXT_LENGTH
            if (reason.codePointCount(0, reason.length) <= max) return reason
            return reason.substring(0, reason.offsetByCodePoints(0, max))
        }

        /** A resolver (archive read) failure as a moderation error. */
        fun moderationError(error: Throwable): ModerationError = when (error) {
            is ModerationError -> error
            ArchiveError.Offline -> ModerationError.Offline
            ArchiveError.PermissionDenied -> ModerationError.PermissionDenied
            is ArchiveError -> ModerationError.Unknown(error.userMessage)
            else -> {
                LogCategory.APP.log.e(error, "Resolving a submission failed")
                ModerationError.Unknown(GENERIC_ERROR_MESSAGE)
            }
        }
    }
}

/** The public archive folder of a published exam's files (the legacy `imagess/` spelling is the contract). */
internal val ExamKind.archiveFolder: String
    get() = when (this) {
        ExamKind.IMAGES -> "imagess"
        ExamKind.PDF -> "pdfs"
    }
