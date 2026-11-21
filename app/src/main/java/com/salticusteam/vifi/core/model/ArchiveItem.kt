package com.salticusteam.vifi.core.model

// Added in 2.5 — what an exam contains, known while listing a lesson's exams.
/** [fileCount] is the number of image pages, or PDF files. */
data class ExamSummary(
    val kind: ExamKind,
    val fileCount: Int,
    // Added in 4.2.0 — published question count; null unless `format == "structured"` with questionCount > 0 (opens the question screen).
    val questionCount: Int? = null,
) {
    /** Whether the exam opens the structured question screen (`ExamRouting`). */
    val isStructured: Boolean get() = (questionCount ?: 0) > 0
}

// Added in 2.5 — one entry of an archive list: a university, faculty, department, lesson or exam.
data class ArchiveItem(
    val path: ArchivePath,
    val level: ArchiveLevel,
    /** Number of child entries (faculties of a university, ...); `null` for exams. */
    val childCount: Int?,
    /** Exams only. `null` when the exam node has no files. */
    val exam: ExamSummary?,
    // Added in 4.2.0 — the course node's country-wide subject (`catalogNodes/<course>.subjectId`), keys the practice pools; lessons only.
    val subjectId: String? = null,
) {
    /** Identity of the item is its path. */
    val id: ArchivePath get() = path

    val name: String get() = path.displayName.orEmpty()
}

// Added in 2.5 — the files of a single exam, ready to be displayed.
/** [fileUrls] are the image pages in reading order, or the PDF file(s). URLs are plain strings. */
data class ExamDocument(
    val path: ArchivePath,
    val kind: ExamKind,
    val fileUrls: List<String>,
    // Added in 4.2.0 — published question count of a `format == "structured"` exam, else null (viewer redirects to questions).
    val questionCount: Int? = null,
) {
    /** Whether the exam opens the structured question screen (`ExamRouting.redirect`). */
    val isStructured: Boolean get() = (questionCount ?: 0) > 0

    val title: String get() = path.displayName.orEmpty()
}
