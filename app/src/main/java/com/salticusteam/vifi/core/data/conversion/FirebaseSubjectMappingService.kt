package com.salticusteam.vifi.core.data.conversion

import com.google.firebase.functions.FirebaseFunctions
import com.salticusteam.vifi.core.data.repository.SubjectMappingService
import com.salticusteam.vifi.core.model.AdminSubject
import com.salticusteam.vifi.core.model.SubjectTarget
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * The admin subject mapping on Firebase (iOS `FirebaseSubjectMappingService`): admin-gated callables in europe-west1
 * (`listSubjects`, `setCourseSubject`, `renameSubject`, `mergeSubjects`, see [SubjectMappingCalls]). Failures map to
 * `ExamConversionError` like the other admin conversion calls.
 *
 * Added in 4.2.0 — the live flavor's subject-mapping service.
 */
class FirebaseSubjectMappingService @Inject constructor(
    private val functions: FirebaseFunctions,
) : SubjectMappingService {

    override suspend fun subjects(): List<AdminSubject> =
        SubjectMappingCalls.subjects(call(SubjectMappingCalls.Name.LIST, emptyMap()))

    override suspend fun setCourseSubject(courseId: String, target: SubjectTarget) {
        call(SubjectMappingCalls.Name.SET_COURSE_SUBJECT, SubjectMappingCalls.setCourseSubject(courseId, target))
    }

    override suspend fun renameSubject(id: String, name: String) {
        call(SubjectMappingCalls.Name.RENAME, SubjectMappingCalls.rename(id, name))
    }

    override suspend fun mergeSubject(id: String, into: String) {
        call(SubjectMappingCalls.Name.MERGE, SubjectMappingCalls.merge(id, into))
    }

    // Helpers
    private suspend fun call(name: String, payload: Map<String, Any>): Any? = try {
        functions.getHttpsCallable(name)
            .withTimeout(FirebaseExamConversionAdminService.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .call(payload)
            .await()
            .getData()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ExamConversionErrors.map(e)
    }
}
