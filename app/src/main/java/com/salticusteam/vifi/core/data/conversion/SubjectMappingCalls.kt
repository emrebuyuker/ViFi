package com.salticusteam.vifi.core.data.conversion

import com.salticusteam.vifi.core.model.AdminSubject
import com.salticusteam.vifi.core.model.SubjectCourse
import com.salticusteam.vifi.core.model.SubjectTarget

/**
 * Callable names, request payloads and the response parser of the admin subject-mapping callables (iOS
 * `SubjectMappingCalls`; server `conversions/subjectsAdmin.js`, europe-west1, admin claim).
 */
object SubjectMappingCalls {

    object Name {
        const val LIST = "listSubjects"
        const val SET_COURSE_SUBJECT = "setCourseSubject"
        const val RENAME = "renameSubject"
        const val MERGE = "mergeSubjects"
    }

    fun setCourseSubject(courseId: String, target: SubjectTarget): Map<String, Any> = when (target) {
        is SubjectTarget.Existing -> mapOf("courseId" to courseId, "subjectId" to target.id)
        is SubjectTarget.New -> mapOf("courseId" to courseId, "newSubjectName" to target.name)
    }

    fun rename(id: String, name: String): Map<String, Any> = mapOf("subjectId" to id, "name" to name)

    fun merge(id: String, into: String): Map<String, Any> = mapOf("fromSubjectId" to id, "intoSubjectId" to into)

    /** `{ subjects: [{ id, name, aliases, courses: [...] }] }`; entries (and courses) without an id are dropped. */
    fun subjects(data: Any?): List<AdminSubject> {
        val entries = ((data as? Map<*, *>)?.get("subjects") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        return entries.mapNotNull { entry ->
            val id = (entry["id"] as? String)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            AdminSubject(
                id = id,
                name = entry["name"] as? String ?: id,
                aliases = (entry["aliases"] as? List<*>).orEmpty().filterIsInstance<String>(),
                courses = (entry["courses"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().mapNotNull(::course),
            )
        }
    }

    private fun course(entry: Map<*, *>): SubjectCourse? {
        val id = (entry["id"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        return SubjectCourse(
            id = id,
            name = entry["name"] as? String ?: id,
            universityId = entry["universityId"] as? String ?: "",
            universityName = entry["universityName"] as? String ?: "",
            facultyName = entry["facultyName"] as? String ?: "",
            departmentName = entry["departmentName"] as? String ?: "",
            questionCount = ((entry["questionCount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
        )
    }
}
