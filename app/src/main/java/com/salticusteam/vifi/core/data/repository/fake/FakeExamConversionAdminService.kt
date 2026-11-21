package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.ExamConversionAdminService
import com.salticusteam.vifi.core.model.AIMonthlyUsage
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.BulkConversionEstimate
import com.salticusteam.vifi.core.model.BulkConversionScope
import com.salticusteam.vifi.core.model.ConversionSource
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ConversionUsage
import com.salticusteam.vifi.core.model.ExamConversion
import com.salticusteam.vifi.core.model.ExamConversionError
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.PublishedExamResult
import com.salticusteam.vifi.core.model.SubmissionFileRef
import com.salticusteam.vifi.core.util.ExamListTitle
import java.time.Instant
import kotlin.time.Duration
import kotlinx.coroutines.delay

/**
 * An in-memory conversion workflow for the mock flavor, previews and tests (iOS `MockExamConversionService`): two
 * ready jobs (one with a list-title suggestion, one whose title matches no list name, K28) and one failed job; actions
 * update the jobs like the server would and are recorded in [calls]. Drafts are the [SampleQuestions].
 */
class FakeExamConversionAdminService(
    jobs: List<ExamConversion> = SAMPLES,
    private var usage: AIMonthlyUsage = AIMonthlyUsage(month = "2026-11", costUsd = 12.4),
    private val latency: Duration = Duration.ZERO,
    private val now: () -> Instant = Instant::now,
) : ExamConversionAdminService {

    private val jobs = jobs.toMutableList()
    private val regenerationLeases = mutableMapOf<Pair<String, Int>, Instant>()

    /** Fails every call when non-null. */
    var error: ExamConversionError? = null

    /** Every call by name and arguments, in order (for tests). */
    val calls = mutableListOf<String>()

    override suspend fun conversions(statuses: Set<ConversionStatus>): List<ExamConversion> = step("conversions $statuses") {
        jobs.filter { it.status in statuses }
    }

    override suspend fun draftQuestions(jobId: String): List<ExamQuestion> = step("draftQuestions $jobId") {
        SampleQuestions.questions(jobId).map { it.copy(regeneratingUntil = regenerationLeases[jobId to it.order]) }
    }

    override suspend fun requestConversion(examId: String, provider: AIProvider): ExamConversion =
        step("requestConversion $examId ${provider.raw}") {
            val source = ConversionSource.Legacy(examId)
            jobs.firstOrNull { it.id == source.jobId && !it.status.isTerminal } ?: ExamConversion(
                id = source.jobId, source = source, kind = ExamKind.IMAGES, status = ConversionStatus.QUEUED,
                provider = provider, attempts = 0, error = null, usage = null, placementNames = emptyList(),
                createdAt = now(), readyAt = null,
            ).also { job ->
                jobs.removeAll { it.id == job.id }
                jobs.add(0, job)
            }
        }

    override suspend fun regenerate(jobId: String, provider: AIProvider) = step("regenerate $jobId ${provider.raw}") {
        update(jobId) { it.copy(status = ConversionStatus.QUEUED, provider = provider) }
    }

    override suspend fun regenerateQuestion(jobId: String, order: Int, provider: AIProvider) =
        step("regenerateQuestion $jobId $order ${provider.raw}") {
            // Like the server's lease: the draft shows as regenerating for a few seconds.
            regenerationLeases[jobId to order] = now().plusSeconds(LEASE_SECONDS)
        }

    override suspend fun saveDraftQuestion(jobId: String, question: ExamQuestion) =
        step("saveDraftQuestion $jobId ${question.order}") { }

    override suspend fun publish(
        jobId: String,
        subjectId: String?,
        newSubjectName: String?,
        examTitle: String?,
    ): PublishedExamResult = step("publish $jobId") {
        // Like the server (K28): the title must come from the list, the admin's or the job's suggestion.
        val title = examTitle ?: jobs.firstOrNull { it.id == jobId }?.suggestedTitle
        if (title?.let(ExamListTitle::parse) == null) throw ExamConversionError.NotAllowed("Sınav adı listeden seçilmeli.")
        update(jobId) { it.copy(status = ConversionStatus.PUBLISHED) }
        PublishedExamResult(examId = "mock-$jobId", questionCount = SampleQuestions.documents.size)
    }

    override suspend fun discard(jobId: String) = step("discard $jobId") {
        update(jobId) { it.copy(status = ConversionStatus.DISCARDED) }
    }

    override suspend fun requestBulkConversion(
        scope: BulkConversionScope,
        provider: AIProvider,
        dryRun: Boolean,
    ): BulkConversionEstimate = step("requestBulkConversion $scope ${provider.raw} $dryRun") {
        val count = 12
        val cost = count * if (provider == AIProvider.CLAUDE) 0.42 else 0.18
        if (!dryRun) usage = usage.copy(costUsd = usage.costUsd + cost)
        BulkConversionEstimate(
            examCount = count,
            estimatedCostUsd = cost,
            jobIds = if (dryRun) emptyList() else (1..count).map { "exam-bulk-$it" },
        )
    }

    override suspend fun monthlyUsage(): AIMonthlyUsage = step("monthlyUsage") { usage }

    override suspend fun updatePublishedQuestion(question: ExamQuestion) = step("updatePublishedQuestion ${question.id}") { }

    override suspend fun regeneratePublishedQuestion(questionId: String, provider: AIProvider) =
        step("regeneratePublishedQuestion $questionId ${provider.raw}") { }

    private suspend fun <T> step(call: String, body: () -> T): T {
        calls += call
        if (latency > Duration.ZERO) delay(latency)
        error?.let { throw it }
        return body()
    }

    private fun update(jobId: String, change: (ExamConversion) -> ExamConversion) {
        val index = jobs.indexOfFirst { it.id == jobId }
        if (index < 0) throw ExamConversionError.NotFound
        jobs[index] = change(jobs[index])
    }

    companion object {
        private const val LEASE_SECONDS = 6L

        val SAMPLES = listOf(
            ExamConversion(
                id = "sub-sub-1", source = ConversionSource.Submission("sub-1"), kind = ExamKind.IMAGES,
                status = ConversionStatus.READY, provider = AIProvider.CLAUDE, attempts = 1, error = null,
                usage = ConversionUsage(inputTokens = 21_000, outputTokens = 6_500, costUsd = 0.42),
                placementNames = listOf("Boğaziçi Üniversitesi", "Mühendislik Fakültesi", "Bilgisayar Mühendisliği", "Ayrık Matematik", "2023 VİZE"),
                createdAt = Instant.ofEpochSecond(1_762_000_000), readyAt = Instant.ofEpochSecond(1_762_000_300),
                files = listOf(SubmissionFileRef("imagess/mock-1.jpg", 1), SubmissionFileRef("imagess/mock-2.jpg", 2)),
                suggestedSubjectId = "ayrik-matematik", suggestedSubjectName = "Ayrık Matematik",
                originalTitle = "2023 VİZE", suggestedTitle = "2023 VİZE",
            ),
            ExamConversion(
                id = "sub-sub-2", source = ConversionSource.Submission("sub-2"), kind = ExamKind.IMAGES,
                status = ConversionStatus.READY, provider = AIProvider.GEMINI, attempts = 1, error = null,
                usage = ConversionUsage(inputTokens = 12_000, outputTokens = 4_000, costUsd = 0.21),
                placementNames = listOf("Boğaziçi Üniversitesi", "Mühendislik Fakültesi", "Bilgisayar Mühendisliği", "Ayrık Matematik", "2021 DERS NOTU"),
                createdAt = Instant.ofEpochSecond(1_761_950_000), readyAt = Instant.ofEpochSecond(1_761_950_300),
                files = listOf(SubmissionFileRef("imagess/mock-3.jpg", 1)),
                originalTitle = "2021 DERS NOTU", suggestedTitle = null,
            ),
            ExamConversion(
                id = "exam-mock-legacy", source = ConversionSource.Legacy("mock-legacy"), kind = ExamKind.PDF,
                status = ConversionStatus.FAILED, provider = AIProvider.GEMINI, attempts = 3, error = "Sayfalar okunamadı",
                usage = null, placementNames = listOf("Sakarya Üniversitesi", "İşletme Fakültesi", "İşletme", "Muhasebe", "2022 FİNAL"),
                createdAt = Instant.ofEpochSecond(1_761_900_000), readyAt = null,
            ),
        )
    }
}
