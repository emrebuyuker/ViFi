package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSummary

/**
 * Deterministic in-memory [ArchiveRepository] for previews and tests: no Firebase, no I/O.
 *
 * Set [error] to make every call throw it; call counters and the last requested path are recorded.
 * Sample tree: two universities, each with one faculty > department > lesson holding two exams (newest first).
 * Like the iOS sample source it never changes ([archiveChanges] keeps the default empty flow).
 */
class FakeArchiveRepository(
    /** When non-null, every call throws it. */
    var error: ArchiveError? = null,
) : ArchiveRepository {

    var itemsCallCount = 0
        private set
    var documentCallCount = 0
        private set
    var prefetchCallCount = 0
        private set
    var lastRequestedPath: ArchivePath? = null
        private set

    private val children = mutableMapOf<ArchivePath, List<ArchiveItem>>()
    private val documents = mutableMapOf<ArchivePath, ExamDocument>()

    init {
        val universities = listOf(
            University("itu", "İstanbul Teknik Üniversitesi", "muh", "Mühendislik", "blg", "Bilgisayar Mühendisliği", "blg101", "BLG 101"),
            University("odtu", "Orta Doğu Teknik Üniversitesi", "fen", "Fen Edebiyat", "mat", "Matematik", "mat101", "MAT 101"),
        )
        children[ArchivePath.ROOT] = universities.map { u ->
            ArchiveItem(ArchivePath.ROOT.appending(u.id, u.name), ArchiveLevel.UNIVERSITY, 1, null)
        }
        universities.forEach { u ->
            val university = ArchivePath.ROOT.appending(u.id, u.name)
            val faculty = university.appending(u.facultyId, u.facultyName)
            val department = faculty.appending(u.departmentId, u.departmentName)
            val lesson = department.appending(u.lessonId, u.lessonName)
            children[university] = listOf(ArchiveItem(faculty, ArchiveLevel.FACULTY, 1, null))
            children[faculty] = listOf(ArchiveItem(department, ArchiveLevel.DEPARTMENT, 1, null))
            children[department] = listOf(ArchiveItem(lesson, ArchiveLevel.LESSON, 2, null))

            // Newest first, like the real archive.
            val pdf = lesson.appending("2023-vize", "2023 Vize")
            val images = lesson.appending("2022-final", "2022 Final")
            children[lesson] = listOf(
                ArchiveItem(pdf, ArchiveLevel.EXAM, null, ExamSummary(ExamKind.PDF, 1)),
                ArchiveItem(images, ArchiveLevel.EXAM, null, ExamSummary(ExamKind.IMAGES, 3)),
            )
            documents[pdf] = ExamDocument(pdf, ExamKind.PDF, listOf("https://example.com/${u.id}/2023-vize.pdf"))
            documents[images] = ExamDocument(
                images,
                ExamKind.IMAGES,
                (1..3).map { "https://example.com/${u.id}/2022-final-$it.jpg" },
            )
        }
    }

    override suspend fun items(path: ArchivePath): List<ArchiveItem> {
        itemsCallCount++
        lastRequestedPath = path
        error?.let { throw it }
        return children[path] ?: throw ArchiveError.NotFound
    }

    override suspend fun document(path: ArchivePath): ExamDocument {
        documentCallCount++
        lastRequestedPath = path
        error?.let { throw it }
        return documents[path] ?: throw ArchiveError.NotFound
    }

    override suspend fun prefetchArchive() {
        prefetchCallCount++
        error?.let { throw it }
    }

    private class University(
        val id: String,
        val name: String,
        val facultyId: String,
        val facultyName: String,
        val departmentId: String,
        val departmentName: String,
        val lessonId: String,
        val lessonName: String,
    )
}
