package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.AIMonthlyUsage
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.BulkConversionEstimate
import com.salticusteam.vifi.core.model.BulkConversionScope
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ExamConversion
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.PublishedExamResult

/**
 * Admin-only AI conversion workflow (4.2.0, iOS `ExamConversionServicing`): the eight admin callables
 * (`ExamConversionCalls.Name`) plus the admin reads of `examConversions`, its draft questions and `aiUsage`. Every
 * action that runs a model takes the provider the admin picked (K9); there is no default. Calls are admin-gated on the
 * server, so they fail for non-admins. Throws `ExamConversionError` on failure.
 */
interface ExamConversionAdminService {
    /** Jobs in [statuses] (e.g. `READY` for the review list, `FAILED` for retries), newest first. */
    suspend fun conversions(statuses: Set<ConversionStatus>): List<ExamConversion>

    /** The draft questions of a job, in order (`examConversions/<jobId>/questions`). */
    suspend fun draftQuestions(jobId: String): List<ExamQuestion>

    /** "Yeni yapıya geçir": queues a legacy exam (`requestExamConversion`); an active job comes back unchanged. */
    suspend fun requestConversion(examId: String, provider: AIProvider): ExamConversion

    /** Regenerates the whole exam (second-approval rejection, K14) or retries a failed job (`regenerateExamConversion`). */
    suspend fun regenerate(jobId: String, provider: AIProvider)

    /** Regenerates a single draft question (K8, `regenerateDraftQuestion`). */
    suspend fun regenerateQuestion(jobId: String, order: Int, provider: AIProvider)

    /**
     * Saves an admin's edit of a draft question (rules allow only `ExamConversionCalls.contentFields` + `needsReview`,
     * with `editedBy == uid` and `editedAt` = server time).
     */
    suspend fun saveDraftQuestion(jobId: String, question: ExamQuestion)

    /**
     * Second approval (`publishExamConversion`): publishes the draft into `questions` and marks the exam structured.
     * [subjectId] picks an existing subject, [newSubjectName] opens one, both `null` keep the server's suggestion;
     * [examTitle] is the list title (K28), `null` keeps the server's suggestion.
     */
    suspend fun publish(jobId: String, subjectId: String?, newSubjectName: String?, examTitle: String?): PublishedExamResult

    /** "Sınavı iptal et" (K14, `discardExamConversion`): gives the job up for good; coins are never taken back. */
    suspend fun discard(jobId: String)

    /** Bulk conversion (K12b, `requestBulkConversion`); with [dryRun] nothing is queued and only the estimate returns. */
    suspend fun requestBulkConversion(scope: BulkConversionScope, provider: AIProvider, dryRun: Boolean): BulkConversionEstimate

    /** This month's AI spend (`aiUsage/<YYYY-MM>`, Europe/Istanbul), zero when nothing was spent yet. */
    suspend fun monthlyUsage(): AIMonthlyUsage

    /** K15 (`updatePublishedQuestion`): edits a published question; live at once, the id does not change. */
    suspend fun updatePublishedQuestion(question: ExamQuestion)

    /** K15 (`regeneratePublishedQuestion`): regenerates one published question with the picked provider. */
    suspend fun regeneratePublishedQuestion(questionId: String, provider: AIProvider)
}
