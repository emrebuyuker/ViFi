package com.salticusteam.vifi.core.data.conversion

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.salticusteam.vifi.core.data.firebase.ExamQuestionParser
import com.salticusteam.vifi.core.data.repository.AuthService
import com.salticusteam.vifi.core.data.repository.ExamConversionAdminService
import com.salticusteam.vifi.core.model.AIMonthlyUsage
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.BulkConversionEstimate
import com.salticusteam.vifi.core.model.BulkConversionScope
import com.salticusteam.vifi.core.model.ConversionSource
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ExamConversion
import com.salticusteam.vifi.core.model.ExamConversionError
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.PublishedExamResult
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * The admin AI-conversion workflow on Firebase (iOS `FirebaseExamConversionService`): job and draft reads from the
 * named 'vifi' Firestore database (`examConversions`, `aiUsage`), the AI-running actions as admin-gated callables in
 * europe-west1 (`ExamConversionCalls.Name`), and draft edits as direct Firestore updates the rules allow (content
 * fields + `editedBy` + `editedAt`, see [DraftEdit]).
 *
 * Documents are turned into models by the Firebase-free [ExamConversionParser] / [ExamQuestionParser] (callers convert
 * `Timestamp` values to `Instant` first); failures are mapped by [ExamConversionErrors]. No call here runs without an
 * admin: the rules and the callables enforce the claim.
 *
 * Added in 4.2.0 — the live flavor's conversion admin service.
 */
class FirebaseExamConversionAdminService @Inject constructor(
    private val functions: FirebaseFunctions,
    private val firestore: FirebaseFirestore,
    private val auth: AuthService,
) : ExamConversionAdminService {

    override suspend fun conversions(statuses: Set<ConversionStatus>): List<ExamConversion> {
        if (statuses.isEmpty()) return emptyList()
        return try {
            firestore.collection(CONVERSIONS)
                .whereIn("status", statuses.map { it.raw })
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(LIST_LIMIT)
                .get()
                .await()
                .documents
                .mapNotNull { ExamConversionParser.conversion(it.id, readingDates(it.data.orEmpty())) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ExamConversionErrors.map(e)
        }
    }

    override suspend fun draftQuestions(jobId: String): List<ExamQuestion> = try {
        val documents = firestore.collection(CONVERSIONS).document(jobId).collection(DRAFTS).get().await().documents
        ExamQuestionParser.questions(documents.map { it.id to readingDates(it.data.orEmpty()) }, examId = jobId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ExamConversionErrors.map(e)
    }

    override suspend fun requestConversion(examId: String, provider: AIProvider): ExamConversion {
        val data = call(ExamConversionCalls.Name.REQUEST_CONVERSION, ExamConversionCalls.requestConversion(examId, provider))
        val source = ConversionSource.Legacy(examId)
        return ExamConversion(
            id = ExamConversionCalls.jobId(data) ?: source.jobId,
            source = source,
            kind = ExamKind.IMAGES,
            status = ExamConversionCalls.status(data) ?: ConversionStatus.QUEUED,
            provider = provider,
            attempts = 0,
            error = null,
            usage = null,
            placementNames = emptyList(),
            createdAt = null,
            readyAt = null,
        )
    }

    override suspend fun regenerate(jobId: String, provider: AIProvider) {
        call(ExamConversionCalls.Name.REGENERATE, ExamConversionCalls.regenerate(jobId, provider))
    }

    override suspend fun regenerateQuestion(jobId: String, order: Int, provider: AIProvider) {
        call(
            ExamConversionCalls.Name.REGENERATE_DRAFT_QUESTION,
            ExamConversionCalls.regenerateDraftQuestion(jobId, order, provider),
        )
    }

    override suspend fun saveDraftQuestion(jobId: String, question: ExamQuestion) {
        val uid = auth.currentUser?.id ?: throw ExamConversionError.PermissionDenied
        val fields = DraftEdit.fields(question, uid, editedAt = FieldValue.serverTimestamp())
        try {
            firestore.collection(CONVERSIONS).document(jobId).collection(DRAFTS)
                .document(DraftEdit.documentId(question.order))
                .update(fields)
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ExamConversionErrors.map(e)
        }
    }

    override suspend fun publish(
        jobId: String,
        subjectId: String?,
        newSubjectName: String?,
        examTitle: String?,
    ): PublishedExamResult {
        val data = call(
            ExamConversionCalls.Name.PUBLISH,
            ExamConversionCalls.publish(jobId, subjectId, newSubjectName, examTitle),
        )
        return ExamConversionCalls.publishResult(data) ?: throw ExamConversionError.NotAllowed(UNEXPECTED_RESPONSE)
    }

    override suspend fun discard(jobId: String) {
        call(ExamConversionCalls.Name.DISCARD, ExamConversionCalls.discard(jobId))
    }

    override suspend fun requestBulkConversion(
        scope: BulkConversionScope,
        provider: AIProvider,
        dryRun: Boolean,
    ): BulkConversionEstimate =
        ExamConversionCalls.bulkEstimate(
            call(ExamConversionCalls.Name.REQUEST_BULK, ExamConversionCalls.requestBulk(scope, provider, dryRun)),
        )

    override suspend fun monthlyUsage(): AIMonthlyUsage {
        val month = AIMonthlyUsage.monthKey(Instant.now())
        return try {
            val snapshot = firestore.collection(USAGE).document(month).get().await()
            ExamConversionCalls.monthlyUsage(month, snapshot.data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ExamConversionErrors.map(e)
        }
    }

    override suspend fun updatePublishedQuestion(question: ExamQuestion) {
        call(ExamConversionCalls.Name.UPDATE_PUBLISHED_QUESTION, ExamConversionCalls.updatePublishedQuestion(question))
    }

    override suspend fun regeneratePublishedQuestion(questionId: String, provider: AIProvider) {
        call(
            ExamConversionCalls.Name.REGENERATE_PUBLISHED_QUESTION,
            ExamConversionCalls.regeneratePublishedQuestion(questionId, provider),
        )
    }

    // Helpers
    /** Calls an admin callable and returns its raw response data. */
    private suspend fun call(name: String, payload: Map<String, Any>): Any? = try {
        functions.getHttpsCallable(name)
            .withTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .call(payload)
            .await()
            .getData()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ExamConversionErrors.map(e)
    }

    internal companion object {
        const val CONVERSIONS = "examConversions"
        const val DRAFTS = "questions"
        const val USAGE = "aiUsage"
        const val LIST_LIMIT = 100L

        /** iOS leaves the callable at the SDK's 70 s default; the AI work itself runs in queued server tasks. */
        const val CALL_TIMEOUT_SECONDS = 70L

        /** The server answered `publishExamConversion` with something unreadable (Turkish, shown as is). */
        const val UNEXPECTED_RESPONSE = "Sunucudan beklenmeyen bir yanıt geldi."

        /** Top-level `Timestamp` values as `Instant`, the shape the Firebase-free parsers expect. */
        fun readingDates(data: Map<String, Any?>): Map<String, Any?> =
            data.mapValues { (_, value) ->
                (value as? Timestamp)?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) } ?: value
            }
    }
}
