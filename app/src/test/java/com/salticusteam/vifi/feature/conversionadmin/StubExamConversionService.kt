package com.salticusteam.vifi.feature.conversionadmin

import com.salticusteam.vifi.core.data.repository.ExamConversionAdminService
import com.salticusteam.vifi.core.model.AIMonthlyUsage
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.BulkConversionEstimate
import com.salticusteam.vifi.core.model.BulkConversionScope
import com.salticusteam.vifi.core.model.ConversionSource
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ConversionUsage
import com.salticusteam.vifi.core.model.ExamConversion
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.PublishedExamResult
import com.salticusteam.vifi.core.model.QuestionAnswer
import com.salticusteam.vifi.core.model.QuestionChoice
import java.time.Instant

/** iOS `StubExamConversionService`: scripted jobs / drafts / answers, recorded calls. Failures come from [error]. */
class StubExamConversionService : ExamConversionAdminService {

    sealed interface Call {
        data class Regenerate(val jobId: String, val provider: AIProvider) : Call
        data class RegenerateQuestion(val jobId: String, val order: Int, val provider: AIProvider) : Call
        data class SaveDraftQuestion(val jobId: String, val questionId: String) : Call
        data class Publish(val jobId: String, val subjectId: String?, val newSubjectName: String?, val examTitle: String?) : Call
        data class Discard(val jobId: String) : Call
        data class RequestBulk(val scope: BulkConversionScope, val provider: AIProvider, val dryRun: Boolean) : Call
        data class RequestConversion(val examId: String, val provider: AIProvider) : Call
        data class UpdatePublishedQuestion(val questionId: String) : Call
        data class RegeneratePublishedQuestion(val questionId: String, val provider: AIProvider) : Call
    }

    var jobs: List<ExamConversion> = emptyList()
    val draftsByJob = mutableMapOf<String, List<ExamQuestion>>()

    /** Answers served first in, first out before [draftsByJob], to script drafts that change while polling. */
    val draftResponses = ArrayDeque<List<ExamQuestion>>()
    var error: Throwable? = null
    var publishResult = PublishedExamResult(examId = "e1", questionCount = 1)
    var bulkEstimate = BulkConversionEstimate(examCount = 0, estimatedCostUsd = 0.0, jobIds = emptyList())
    var usage = AIMonthlyUsage(month = "2026-11", costUsd = 0.0)
    var conversionListCount = 0
        private set
    var draftCallCount = 0
        private set
    val calls = mutableListOf<Call>()

    override suspend fun conversions(statuses: Set<ConversionStatus>): List<ExamConversion> {
        conversionListCount++
        error?.let { throw it }
        return jobs.filter { it.status in statuses }
    }

    override suspend fun draftQuestions(jobId: String): List<ExamQuestion> {
        draftCallCount++
        error?.let { throw it }
        return draftResponses.removeFirstOrNull() ?: draftsByJob[jobId].orEmpty()
    }

    override suspend fun requestConversion(examId: String, provider: AIProvider): ExamConversion {
        calls += Call.RequestConversion(examId, provider)
        error?.let { throw it }
        return ConversionFixture.job(id = "exam-$examId", status = ConversionStatus.QUEUED, provider = provider)
    }

    override suspend fun regenerate(jobId: String, provider: AIProvider) {
        calls += Call.Regenerate(jobId, provider)
        error?.let { throw it }
    }

    override suspend fun regenerateQuestion(jobId: String, order: Int, provider: AIProvider) {
        calls += Call.RegenerateQuestion(jobId, order, provider)
        error?.let { throw it }
    }

    override suspend fun saveDraftQuestion(jobId: String, question: ExamQuestion) {
        calls += Call.SaveDraftQuestion(jobId, question.id)
        error?.let { throw it }
    }

    override suspend fun publish(
        jobId: String,
        subjectId: String?,
        newSubjectName: String?,
        examTitle: String?,
    ): PublishedExamResult {
        calls += Call.Publish(jobId, subjectId, newSubjectName, examTitle)
        error?.let { throw it }
        return publishResult
    }

    override suspend fun discard(jobId: String) {
        calls += Call.Discard(jobId)
        error?.let { throw it }
    }

    override suspend fun requestBulkConversion(
        scope: BulkConversionScope,
        provider: AIProvider,
        dryRun: Boolean,
    ): BulkConversionEstimate {
        calls += Call.RequestBulk(scope, provider, dryRun)
        error?.let { throw it }
        return bulkEstimate
    }

    override suspend fun monthlyUsage(): AIMonthlyUsage {
        error?.let { throw it }
        return usage
    }

    override suspend fun updatePublishedQuestion(question: ExamQuestion) {
        calls += Call.UpdatePublishedQuestion(question.id)
        error?.let { throw it }
    }

    override suspend fun regeneratePublishedQuestion(questionId: String, provider: AIProvider) {
        calls += Call.RegeneratePublishedQuestion(questionId, provider)
        error?.let { throw it }
    }
}

/** Builders for the conversion admin tests (iOS `ConversionFixture`). */
object ConversionFixture {

    fun job(
        id: String = "sub-s1",
        status: ConversionStatus = ConversionStatus.READY,
        provider: AIProvider? = AIProvider.CLAUDE,
        error: String? = null,
        originalTitle: String = "2023 VİZE",
        suggestedTitle: String? = "2023 VİZE",
    ) = ExamConversion(
        id = id,
        source = ConversionSource.Submission("s1"),
        kind = ExamKind.IMAGES,
        status = status,
        provider = provider,
        attempts = 1,
        error = error,
        usage = ConversionUsage(inputTokens = 100, outputTokens = 50, costUsd = 0.42),
        placementNames = listOf("Uni", "Fak", "Böl", "Ders", originalTitle),
        createdAt = null,
        readyAt = null,
        originalTitle = originalTitle,
        suggestedTitle = suggestedTitle,
    )

    fun question(
        order: Int = 1,
        needsReview: Boolean = false,
        regeneratingUntil: Instant? = null,
        regenerationError: String? = null,
        updatedAt: Instant? = null,
    ) = ExamQuestion(
        id = "%03d".format(order),
        examId = "sub-s1",
        order = order,
        label = order.toString(),
        text = "2 + 2 kaçtır?",
        speech = "iki artı iki kaçtır",
        choices = listOf(QuestionChoice("A", "3"), QuestionChoice("B", "4")),
        figures = emptyList(),
        answer = QuestionAnswer(text = "4", choice = "B", speech = null),
        solution = listOf("2 + 2 = 4"),
        topics = listOf("toplama"),
        difficulty = 1,
        confidence = 0.9,
        needsReview = needsReview,
        updatedAt = updatedAt,
        regeneratingUntil = regeneratingUntil,
        regenerationError = regenerationError,
    )
}
