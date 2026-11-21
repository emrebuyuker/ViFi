package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.AdminSubject
import com.salticusteam.vifi.core.model.SubjectTarget

/**
 * Admin-only subject mapping (4.2.0 practice mode, P5; iOS `SubjectMappingServicing`): the four callables of
 * `SubjectMappingCalls.Name`. Admin-gated on the server. Throws `ExamConversionError` on failure.
 */
interface SubjectMappingService {
    /** Every subject with its courses and question counts (`listSubjects`). */
    suspend fun subjects(): List<AdminSubject>

    /** Points a course at an existing or a new subject; its questions and pools move with it (`setCourseSubject`). */
    suspend fun setCourseSubject(courseId: String, target: SubjectTarget)

    /** Renames a subject; its id never changes and the old name becomes an alias (`renameSubject`). */
    suspend fun renameSubject(id: String, name: String)

    /** Moves every course of [id] into [into] and deletes [id] (`mergeSubjects`). */
    suspend fun mergeSubject(id: String, into: String)
}
