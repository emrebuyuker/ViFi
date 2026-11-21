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
    /**
     * Adds the 4.2.0 structured sample (iOS `mockStructuredExam`): KOCAELİ ÜNİVERSİTESİ › MÜHENDİSLİK FAKÜLTESİ ›
     * MATEMATİK BÖLÜMÜ › TOPOLOJİ 1 (subject [FakePoolIndexReader.SAMPLE_SUBJECT_ID]) › 2003 FİNAL, a structured exam
     * with the 3 [SampleQuestions]. Off by default so existing tests keep their two-university tree; on in the mock flavor.
     */
    includeStructuredSample: Boolean = false,
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
            children[department] = listOf(ArchiveItem(lesson, ArchiveLevel.LESSON, 4, null))

            // Newest first, like the real archive. The two oldest are free; the newer two are coin-locked.
            val pdf = lesson.appending("2023-vize", "2023 Vize")
            val images = lesson.appending("2022-final", "2022 Final")
            val olderPdfs = listOf("2021-vize" to "2021 Vize", "2020-final" to "2020 Final")
                .map { (id, name) -> lesson.appending(id, name) }
            children[lesson] = listOf(
                ArchiveItem(pdf, ArchiveLevel.EXAM, null, ExamSummary(ExamKind.PDF, 1)),
                ArchiveItem(images, ArchiveLevel.EXAM, null, ExamSummary(ExamKind.IMAGES, 3)),
            ) + olderPdfs.map { ArchiveItem(it, ArchiveLevel.EXAM, null, ExamSummary(ExamKind.PDF, 1)) }
            olderPdfs.forEach { path ->
                documents[path] = ExamDocument(path, ExamKind.PDF, listOf("https://example.com/${u.id}/${path.components.last()}.pdf"))
            }
            documents[pdf] = ExamDocument(pdf, ExamKind.PDF, listOf("https://example.com/${u.id}/2023-vize.pdf"))
            documents[images] = ExamDocument(
                images,
                ExamKind.IMAGES,
                (1..3).map { "https://example.com/${u.id}/2022-final-$it.jpg" },
            )
        }
        if (includeStructuredSample) addStructuredSample()
    }

    private fun addStructuredSample() {
        val university = ArchivePath.ROOT.appending("kocaeli-universitesi", "KOCAELİ ÜNİVERSİTESİ")
        val faculty = university.appending("kocaeli-muhendislik", "MÜHENDİSLİK FAKÜLTESİ")
        val department = faculty.appending("kocaeli-muhendislik-matematik", "MATEMATİK BÖLÜMÜ")
        val lesson = department.appending(FakePoolIndexReader.SAMPLE_COURSE_ID, "TOPOLOJİ 1")
        val exam = lesson.appending(SampleQuestions.EXAM_ID, "2003 FİNAL")
        val questionCount = SampleQuestions.documents.size
        children[ArchivePath.ROOT] = children[ArchivePath.ROOT].orEmpty() +
            ArchiveItem(university, ArchiveLevel.UNIVERSITY, 1, null)
        children[university] = listOf(ArchiveItem(faculty, ArchiveLevel.FACULTY, 1, null))
        children[faculty] = listOf(ArchiveItem(department, ArchiveLevel.DEPARTMENT, 1, null))
        children[department] = listOf(
            ArchiveItem(lesson, ArchiveLevel.LESSON, 1, null, subjectId = FakePoolIndexReader.SAMPLE_SUBJECT_ID),
        )
        children[lesson] = listOf(
            ArchiveItem(exam, ArchiveLevel.EXAM, null, ExamSummary(ExamKind.IMAGES, 3, questionCount = questionCount)),
        )
        documents[exam] = ExamDocument(
            exam,
            ExamKind.IMAGES,
            (1..3).map { "https://example.com/kocaeli/2003-final-$it.jpg" },
            questionCount = questionCount,
        )
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
