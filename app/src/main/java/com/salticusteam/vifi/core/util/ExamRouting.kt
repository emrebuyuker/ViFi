package com.salticusteam.vifi.core.util

import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.navigation.ViFiRoute

// Added in 4.2.0 — the one decision behind opening an exam: the question screen or the file viewer (iOS `ExamRouting`).
/**
 * An exam opens the structured question screen only when it is `format == "structured"` **and** has questions
 * (`questionCount > 0`, already folded into `questionCount` by the parser); otherwise the original files, which
 * structured exams keep for old clients. No remote switch (K25). The lock rule is not part of this decision and is
 * unchanged: a locked exam never gets here (`ExamLock`, `ExamAccessResolver`).
 */
object ExamRouting {

    fun opensQuestions(questionCount: Int?): Boolean = (questionCount ?: 0) > 0

    /** The route for an exam whose list summary is known (Browse and its search). */
    fun route(path: ArchivePath, kind: ExamKind, questionCount: Int?): ViFiRoute =
        if (opensQuestions(questionCount)) ViFiRoute.StructuredExam(path, kind) else ViFiRoute.Exam(path, kind)

    /** The route of a listed exam, or `null` for an entry without a summary (no files). */
    fun route(item: ArchiveItem): ViFiRoute? = item.exam?.let { route(item.path, it.kind, it.questionCount) }

    /**
     * For a file viewer opened without a summary (recents, a tapped notification) that has now loaded the exam's
     * document: the question route to replace it with, or `null` to stay.
     */
    fun redirect(document: ExamDocument): ViFiRoute.StructuredExam? =
        if (opensQuestions(document.questionCount)) ViFiRoute.StructuredExam(document.path, document.kind) else null
}
