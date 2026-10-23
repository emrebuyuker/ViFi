package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveNameMatch
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.SubmissionLocation
import com.salticusteam.vifi.core.model.SubmissionTier
import javax.inject.Inject

/**
 * How a submission resolves against the live archive (iOS `ResolvedPlacement`): the novelty tier, plus the ids
 * of the catalog nodes that already exist, so a publisher reuses them and only creates what is missing.
 */
data class ResolvedPlacement(
    val tier: SubmissionTier,
    val universityId: String? = null,
    val facultyId: String? = null,
    val departmentId: String? = null,
    val courseId: String? = null,
    /** The existing exam's id when the submission duplicates one. */
    val examId: String? = null,
)

/**
 * Works out a submission's novelty tier against the live archive, and the existing nodes it maps onto (iOS
 * `SubmissionTierResolver`).
 *
 * Reused by the add-exam estimate (tier only, non-binding) and the moderation approval (the full placement).
 * Submissions carry bundled display names, not archive ids, so each level is matched by name
 * ([ArchiveNameMatch]). The university match is reliable (there are only a handful); the department is looked
 * up across every faculty of the matched university, because the bundled faculty name does not always match
 * the archive's; the faculty name only tells a brand-new faculty apart from a new department under an existing
 * one. UI-free; reads only through [ArchiveRepository], so it is cancellable (a cancelled read propagates
 * `CancellationException`) and any `ArchiveError` reaches the caller.
 */
class SubmissionTierResolver @Inject constructor(
    private val repository: ArchiveRepository,
) {

    /** The tier for a submission at [location]. */
    suspend fun tier(location: SubmissionLocation): SubmissionTier = placement(location).tier

    /** The full placement: the tier plus the ids of the archive nodes that already exist for this submission. */
    suspend fun placement(location: SubmissionLocation): ResolvedPlacement {
        val universities = repository.items(ArchivePath.ROOT)
        val university = universities.firstOrNull { ArchiveNameMatch.matches(it.name, location.university) }
            ?: return ResolvedPlacement(SubmissionTier.NEW_UNIVERSITY)
        val universityId = university.id()

        // The department is searched under every faculty (faculty names are unreliable); the faculty-name match
        // only tells a new faculty from a new department when the department itself is missing.
        var facultyNameMatch: ArchiveItem? = null
        var departmentMatch: ArchiveItem? = null
        var facultyOfDepartment: ArchiveItem? = null
        for (faculty in repository.items(university.path)) {
            if (facultyNameMatch == null && ArchiveNameMatch.matches(faculty.name, location.faculty)) {
                facultyNameMatch = faculty
            }
            val match = repository.items(faculty.path).firstOrNull {
                ArchiveNameMatch.matchesDepartment(archive = it.name, profile = location.department)
            }
            if (match != null) {
                departmentMatch = match
                facultyOfDepartment = faculty
                break
            }
        }

        if (departmentMatch == null || facultyOfDepartment == null) {
            return if (facultyNameMatch != null) {
                ResolvedPlacement(SubmissionTier.NEW_DEPARTMENT, universityId, facultyId = facultyNameMatch.id())
            } else {
                ResolvedPlacement(SubmissionTier.NEW_FACULTY, universityId)
            }
        }
        val facultyId = facultyOfDepartment.id()
        val departmentId = departmentMatch.id()

        val course = repository.items(departmentMatch.path).firstOrNull {
            ArchiveNameMatch.matches(it.name, location.lesson)
        } ?: return ResolvedPlacement(SubmissionTier.NEW_COURSE, universityId, facultyId, departmentId)
        val courseId = course.id()

        val exam = repository.items(course.path).firstOrNull { ArchiveNameMatch.matches(it.name, location.examText) }
        return if (exam != null) {
            ResolvedPlacement(SubmissionTier.DUPLICATE, universityId, facultyId, departmentId, courseId, exam.id())
        } else {
            ResolvedPlacement(SubmissionTier.NEW_EXAM, universityId, facultyId, departmentId, courseId)
        }
    }

    /** The node's own (last-level) id. */
    private fun ArchiveItem.id(): String? = path.components.lastOrNull()
}
