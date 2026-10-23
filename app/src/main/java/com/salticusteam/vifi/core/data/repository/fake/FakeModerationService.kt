package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.ModerationService
import com.salticusteam.vifi.core.model.ApprovalResult
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSubmissionRecord
import com.salticusteam.vifi.core.model.ModerationError
import com.salticusteam.vifi.core.model.SubmissionFileRef
import com.salticusteam.vifi.core.model.SubmissionTier
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * In-memory moderation for the mock flavor, previews and tests (iOS `MockModerationService`): a small pending
 * queue; approving or rejecting removes an item. Nothing reaches Firebase; the preview URLs are answered by the
 * mock build's sample files.
 */
class FakeModerationService(
    pending: List<ExamSubmissionRecord> = SAMPLES,
    private val latency: Duration = 300.milliseconds,
    /** What a successful approval reports. */
    private val approval: (ExamSubmissionRecord) -> ApprovalResult = {
        ApprovalResult(tier = SubmissionTier.NEW_UNIVERSITY, coins = 500, publishedExamId = "pub-${it.id}")
    },
) : ModerationService {

    private val pending = pending.toMutableList()

    /** Thrown by every call instead of succeeding, to exercise the failure paths. */
    var failure: ModerationError? = null

    /** Every archive path an admin deleted, in order (for tests). */
    val deletedExams = mutableListOf<ArchivePath>()

    /** Every rejection as (submission id, reason), in order (for tests). */
    val rejections = mutableListOf<Pair<String, String?>>()

    override suspend fun pendingSubmissions(): List<ExamSubmissionRecord> {
        delay(latency)
        failure?.let { throw it }
        return pending.toList()
    }

    override suspend fun approve(record: ExamSubmissionRecord): ApprovalResult {
        delay(latency)
        failure?.let { throw it }
        if (pending.none { it.id == record.id }) throw ModerationError.NotPending
        pending.removeAll { it.id == record.id }
        return approval(record)
    }

    override suspend fun reject(record: ExamSubmissionRecord, reason: String?) {
        delay(latency)
        failure?.let { throw it }
        if (pending.none { it.id == record.id }) throw ModerationError.NotPending
        pending.removeAll { it.id == record.id }
        rejections += record.id to reason
    }

    override suspend fun deleteExam(path: ArchivePath) {
        delay(latency)
        failure?.let { throw it }
        deletedExams += path
    }

    override fun fileUrls(record: ExamSubmissionRecord): List<String> =
        record.files.sortedBy { it.order }.map { "$SAMPLE_HOST/${it.storagePath}" }

    companion object {
        private const val SAMPLE_HOST = "https://example.com"

        /** iOS MockModerationService's queue: an image submission and a PDF one. */
        val SAMPLES = listOf(
            ExamSubmissionRecord(
                id = "sub-1",
                ownerId = "mock-user",
                university = "Boğaziçi Üniversitesi",
                faculty = "Mühendislik Fakültesi",
                department = "Bilgisayar Mühendisliği",
                lesson = "Ayrık Matematik",
                exam = "2023 VİZE",
                kind = ExamKind.IMAGES,
                status = "pending",
                createdAt = Instant.ofEpochSecond(1_760_000_000),
                files = listOf(
                    SubmissionFileRef(storagePath = "pending/mock-user/sub-1/page-1.jpg", order = 1),
                    SubmissionFileRef(storagePath = "pending/mock-user/sub-1/page-2.jpg", order = 2),
                ),
            ),
            ExamSubmissionRecord(
                id = "sub-2",
                ownerId = "mock-user",
                university = "Sakarya Üniversitesi",
                faculty = "İşletme Fakültesi",
                department = "İşletme",
                lesson = "Muhasebe",
                exam = "2022 FİNAL",
                kind = ExamKind.PDF,
                status = "pending",
                createdAt = Instant.ofEpochSecond(1_759_000_000),
                files = listOf(SubmissionFileRef(storagePath = "pending/mock-user/sub-2/exam.pdf", order = 1)),
            ),
        )
    }
}
