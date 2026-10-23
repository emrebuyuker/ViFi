package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.model.ExamSubmissionRecord
import com.salticusteam.vifi.core.model.SubmissionFileRef
import com.salticusteam.vifi.core.util.Slug

// Added in 2.5 — a catalog node created when an approved submission is published (`catalogNodes/<id>`).
/**
 * Matches the archive's composite-slug id scheme and document shape
 * (`{ level, name, parentId, status, childCount, slug, source }`) (iOS `CatalogNodeWrite`).
 */
data class CatalogNodeWrite(
    val id: String,
    /** `university` | `faculty` | `department` | `course`. */
    val level: String,
    val name: String,
    /** The parent node's id; empty for a university (the archive root). */
    val parentId: String,
    val childCount: Int,
) {
    /** The Firestore document fields for this node (user-contributed, so `source` is `submission`). */
    val fields: Map<String, Any>
        get() = mapOf(
            "level" to level,
            "name" to name,
            "parentId" to parentId,
            "status" to ArchivePublishPlan.PUBLISHED,
            "childCount" to childCount,
            "slug" to Slug.make(name),
            "source" to ArchivePublishPlan.SOURCE_SUBMISSION,
        )
}

// Added in 2.5 — what publishing one approved submission writes into the archive.
/**
 * Which catalog nodes to create, which existing parents to bump, and the exam document to write
 * (iOS `ArchivePublishPlan`).
 *
 * Pure and Firestore-free so the id composition and `childCount` bookkeeping are unit-tested. Ids follow the
 * migration's scheme exactly: a university id is `slug(name)`, and each level below is `parentId__slug(name)`,
 * so a free-typed name yields the same id the bundled catalog and FCM topics use.
 */
data class ArchivePublishPlan(
    /** Catalog nodes that do not exist yet and must be created. */
    val nodesToCreate: List<CatalogNodeWrite>,
    /** Ids of existing catalog nodes whose `childCount` grows by one (a new child was added under them). */
    val childCountIncrements: List<String>,
    /**
     * The exam document id to create, or `null` when the submission duplicates an existing exam (nothing new is
     * published — only the low-tier reward is granted).
     */
    val examId: String?,
    val universityId: String,
    val facultyId: String,
    val departmentId: String,
    val courseId: String,
) {

    /**
     * The `exams/<id>` fields, given the files already copied into the public archive storage. Matches the
     * migration's document shape; `source` marks it user-contributed.
     */
    fun examDocument(record: ExamSubmissionRecord, files: List<SubmissionFileRef>): Map<String, Any> = mapOf(
        "title" to record.exam,
        "universityId" to universityId,
        "facultyId" to facultyId,
        "departmentId" to departmentId,
        "courseId" to courseId,
        "universityName" to record.university,
        "facultyName" to record.faculty,
        "departmentName" to record.department,
        "courseName" to record.lesson,
        "kind" to record.kind.raw,
        "files" to files.sortedBy { it.order }.map { mapOf("storagePath" to it.storagePath, "order" to it.order) },
        "status" to PUBLISHED,
        "source" to SOURCE_SUBMISSION,
        "isLocked" to false,
        "coinCost" to 0,
        "counts" to mapOf("views" to 0, "downloads" to 0, "unlocks" to 0),
        "sortKey" to record.exam,
    )

    companion object {
        /** `status` of everything the archive lists. */
        internal const val PUBLISHED = "published"

        /** `source` of archive content that came from a user submission. */
        internal const val SOURCE_SUBMISSION = "submission"

        /** Joins a parent id and a child's slug into the child's id. */
        private const val ID_SEPARATOR = "__"

        fun make(record: ExamSubmissionRecord, placement: ResolvedPlacement): ArchivePublishPlan {
            val universityId = placement.universityId ?: Slug.make(record.university)
            val facultyId = placement.facultyId ?: childId(universityId, record.faculty)
            val departmentId = placement.departmentId ?: childId(facultyId, record.department)
            val courseId = placement.courseId ?: childId(departmentId, record.lesson)

            val createUniversity = placement.universityId == null
            val createFaculty = placement.facultyId == null
            val createDepartment = placement.departmentId == null
            val createCourse = placement.courseId == null
            val isDuplicate = placement.examId != null

            val nodes = mutableListOf<CatalogNodeWrite>()
            val increments = mutableListOf<String>()

            // Nothing new is published for a duplicate: the exam already exists, so no node or count changes.
            if (!isDuplicate) {
                // Each level is either created (its only child is the one in this chain, so childCount is 1) or,
                // when it already exists and its child is newly created, bumped by one.
                if (createCourse) {
                    nodes += CatalogNodeWrite(courseId, "course", record.lesson, departmentId, childCount = 1)
                } else {
                    increments += courseId
                }
                if (createDepartment) {
                    nodes += CatalogNodeWrite(departmentId, "department", record.department, facultyId, childCount = 1)
                } else if (createCourse) {
                    increments += departmentId
                }
                if (createFaculty) {
                    nodes += CatalogNodeWrite(facultyId, "faculty", record.faculty, universityId, childCount = 1)
                } else if (createDepartment) {
                    increments += facultyId
                }
                if (createUniversity) {
                    nodes += CatalogNodeWrite(universityId, "university", record.university, "", childCount = 1)
                } else if (createFaculty) {
                    increments += universityId
                }
            }

            return ArchivePublishPlan(
                nodesToCreate = nodes,
                childCountIncrements = increments,
                examId = if (isDuplicate) null else childId(courseId, record.exam),
                universityId = universityId,
                facultyId = facultyId,
                departmentId = departmentId,
                courseId = courseId,
            )
        }

        private fun childId(parentId: String, name: String): String = parentId + ID_SEPARATOR + Slug.make(name)
    }
}
