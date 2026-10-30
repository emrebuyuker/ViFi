package com.salticusteam.vifi.core.data.archive

import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamLock
import javax.inject.Inject

/** Whether an exam reached from outside its list (a notification) may open (iOS `ExamAccessResolver.Decision`). */
sealed interface ExamAccess {
    data object Open : ExamAccess

    /** Behind the coin lock: [exam] is the archive's own item, for the unlock sheet. */
    data class Locked(val exam: ArchiveItem) : ExamAccess

    /** Not in the archive any more, or without files. */
    data object Unavailable : ExamAccess
}

/**
 * Applies the archive's lock rule to an exam opened from a notification (iOS `ExamAccessResolver`), so a
 * notification never opens a locked exam for free.
 *
 * A subscriber and an exam already unlocked open without loading the course (the viewer handles an exam that was
 * deleted). Otherwise the whole course is loaded: the exam must still be listed with files, and the free ones are the
 * oldest [ExamLock.FREE_COUNT] of the full, unfiltered list (file-less exams take slots, as in the archive).
 */
class ExamAccessResolver @Inject constructor(
    private val archiveRepository: ArchiveRepository,
) {

    /** @throws ArchiveError when the course cannot be read (a deleted course is [ExamAccess.Unavailable]). */
    suspend fun access(examPath: ArchivePath, isSubscribed: Boolean, unlockedIds: Set<String>): ExamAccess {
        val coursePath = examPath.parent
        val examId = examPath.components.lastOrNull()
        if (examPath.level != ArchiveLevel.EXAM || coursePath == null || examId == null) return ExamAccess.Unavailable
        if (isSubscribed || examId in unlockedIds) return ExamAccess.Open

        val exams = try {
            archiveRepository.items(coursePath)
        } catch (e: ArchiveError.NotFound) {
            return ExamAccess.Unavailable
        }
        val exam = exams.firstOrNull { it.path == examPath && it.exam != null } ?: return ExamAccess.Unavailable
        val freeIds = ExamLock.freeExamIds(exams)
        return if (ExamLock.isLocked(exam, freeIds, unlockedIds)) ExamAccess.Locked(exam) else ExamAccess.Open
    }
}
