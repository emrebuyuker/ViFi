package com.salticusteam.vifi.core.data.conversion

import com.salticusteam.vifi.core.data.moderation.FirebaseSubmitterProfileReader
import com.salticusteam.vifi.core.data.repository.ExamConversionAdminService
import com.salticusteam.vifi.core.data.repository.SubjectMappingService
import com.salticusteam.vifi.core.data.repository.SubmitterProfileReader
import com.salticusteam.vifi.core.model.AIMonthlyUsage
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.AdminSubject
import com.salticusteam.vifi.core.model.BulkConversionEstimate
import com.salticusteam.vifi.core.model.BulkConversionScope
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ExamConversion
import com.salticusteam.vifi.core.model.ExamConversionError
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.PublishedExamResult
import com.salticusteam.vifi.core.model.SubjectTarget
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Admin conversion, subject mapping and submitter bindings of the live flavor (4.2.0). SCT-01 wires placeholders (iOS's
 * `Unavailable…` / `Noop…` defaults) so the graph builds; Hat D replaces them with the callable/Firestore services
 * (ADX-01…06, over `ExamConversionCalls` / `SubjectMappingCalls`) and the RTDB submitter reader (ADM-03).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ConversionModule {

    @Binds
    @Singleton
    abstract fun bindExamConversionAdminService(impl: UnavailableExamConversionAdminService): ExamConversionAdminService

    @Binds
    @Singleton
    abstract fun bindSubjectMappingService(impl: UnavailableSubjectMappingService): SubjectMappingService

    /** ADM-03: the RTDB `users/<uid>/profile` reader (name and affiliation only). */
    @Binds
    @Singleton
    abstract fun bindSubmitterProfileReader(impl: FirebaseSubmitterProfileReader): SubmitterProfileReader
}

/** Placeholder until Hat D: every call fails, so an admin screen shows an error instead of silently doing nothing. */
class UnavailableExamConversionAdminService @Inject constructor() : ExamConversionAdminService {
    private val failure get() = ExamConversionError.ServiceUnavailable

    override suspend fun conversions(statuses: Set<ConversionStatus>): List<ExamConversion> = throw failure
    override suspend fun draftQuestions(jobId: String): List<ExamQuestion> = throw failure
    override suspend fun requestConversion(examId: String, provider: AIProvider): ExamConversion = throw failure
    override suspend fun regenerate(jobId: String, provider: AIProvider): Unit = throw failure
    override suspend fun regenerateQuestion(jobId: String, order: Int, provider: AIProvider): Unit = throw failure
    override suspend fun saveDraftQuestion(jobId: String, question: ExamQuestion): Unit = throw failure
    override suspend fun publish(
        jobId: String,
        subjectId: String?,
        newSubjectName: String?,
        examTitle: String?,
    ): PublishedExamResult = throw failure
    override suspend fun discard(jobId: String): Unit = throw failure
    override suspend fun requestBulkConversion(
        scope: BulkConversionScope,
        provider: AIProvider,
        dryRun: Boolean,
    ): BulkConversionEstimate = throw failure
    override suspend fun monthlyUsage(): AIMonthlyUsage = throw failure
    override suspend fun updatePublishedQuestion(question: ExamQuestion): Unit = throw failure
    override suspend fun regeneratePublishedQuestion(questionId: String, provider: AIProvider): Unit = throw failure
}

/** Placeholder until ADX-06: every call fails readably. */
class UnavailableSubjectMappingService @Inject constructor() : SubjectMappingService {
    private val failure get() = ExamConversionError.SubjectMappingUnavailable

    override suspend fun subjects(): List<AdminSubject> = throw failure
    override suspend fun setCourseSubject(courseId: String, target: SubjectTarget): Unit = throw failure
    override suspend fun renameSubject(id: String, name: String): Unit = throw failure
    override suspend fun mergeSubject(id: String, into: String): Unit = throw failure
}
