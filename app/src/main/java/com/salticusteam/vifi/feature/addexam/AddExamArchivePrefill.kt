package com.salticusteam.vifi.feature.addexam

import com.salticusteam.vifi.core.data.catalog.UniversityCatalog
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchiveNameMatch
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.CatalogItem
import com.salticusteam.vifi.core.model.CatalogSelection

/*
 * Pre-fill of the "Sınav Ekle" form from the archive location it was opened from (QA-04): the unlock sheet of a
 * locked exam passes the exam's lesson, so a user whose profile is at another school gets that school and lesson
 * instead of their own. A form opened without a location (the home screen) keeps the profile pre-fill.
 */

/** [this] with the lesson of [context] picked, as if chosen from the archive lesson list. */
internal fun AddExamUiState.withArchiveLesson(context: ArchivePath): AddExamUiState {
    val lesson = context.nameAt(ArchiveLevel.LESSON) ?: return this
    return copy(lesson = CatalogSelection.archive(lesson))
}

/**
 * [this] with its empty school rows filled from [context]: each archive name is matched to the bundled [catalog]
 * the way the lesson picker and the moderation placement match ([ArchiveNameMatch]); a level without a match stays
 * empty for the user to pick. A row that already holds a value is kept.
 */
internal fun AddExamUiState.withArchiveSchool(context: ArchivePath, catalog: UniversityCatalog): AddExamUiState =
    copy(
        university = university.ifEmpty {
            context.match(ArchiveLevel.UNIVERSITY, catalog.universities, ArchiveNameMatch::matches)
        },
        faculty = faculty.ifEmpty { context.match(ArchiveLevel.FACULTY, catalog.faculties, ::matchesFaculty) },
        department = department.ifEmpty {
            context.match(ArchiveLevel.DEPARTMENT, catalog.departments) { archive, bundled ->
                ArchiveNameMatch.matchesDepartment(archive = archive, profile = bundled)
            }
        },
    )

/** Folded " fakültesi", which the bundled faculty names carry and archive faculty names may omit. */
private const val FACULTY_SUFFIX = " fakultesi"

/** Whether the archive faculty [archive] is the bundled [bundled] one ("MÜHENDİSLİK" is "Mühendislik Fakültesi"). */
private fun matchesFaculty(archive: String, bundled: String): Boolean =
    ArchiveNameMatch.key(archive).removeSuffix(FACULTY_SUFFIX) == ArchiveNameMatch.key(bundled).removeSuffix(FACULTY_SUFFIX)

/** The display name of [level] on this path, `null` when the path does not reach it. */
private fun ArchivePath.nameAt(level: ArchiveLevel): String? =
    displayNames.getOrNull(level.depth - 1)?.takeIf { it.isNotBlank() }

/** The bundled item matching this path's [level] name, or an empty selection when there is none. */
private fun ArchivePath.match(
    level: ArchiveLevel,
    items: List<CatalogItem>,
    matches: (archive: String, bundled: String) -> Boolean,
): CatalogSelection {
    val name = nameAt(level) ?: return CatalogSelection.EMPTY
    return items.firstOrNull { matches(name, it.name) }?.let(CatalogSelection::catalog) ?: CatalogSelection.EMPTY
}

private inline fun CatalogSelection.ifEmpty(fallback: () -> CatalogSelection): CatalogSelection =
    if (isEmpty) fallback() else this
