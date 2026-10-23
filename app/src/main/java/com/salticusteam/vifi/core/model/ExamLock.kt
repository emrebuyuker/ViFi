package com.salticusteam.vifi.core.model

// Added in 2.5 — the free-exam rule and the coin cost of unlocking the rest.
/**
 * Every course offers its oldest [FREE_COUNT] exams to everyone; the rest are locked until the user spends
 * coins to unlock them. Exam lists arrive newest-first, so the oldest (the free ones) are at the end.
 * Pure and Firebase-free so the rule is unit-tested.
 */
object ExamLock {
    /** How many of each course's oldest exams are free. */
    const val FREE_COUNT = 2

    /** Coins charged to unlock one exam (kept in step with the Cloud Function's `UNLOCK_COST`). */
    const val UNLOCK_COST = 100

    /** The ids of the free exams: the [FREE_COUNT] oldest, i.e. the last ones of the newest-first [orderedExams]. */
    fun freeExamIds(orderedExams: List<ArchiveItem>): Set<String> =
        orderedExams.takeLast(FREE_COUNT).mapNotNull { it.path.components.lastOrNull() }.toSet()

    /**
     * Whether [exam] can be opened: it is one of the free exams, or the user has unlocked it. A non-exam item
     * (or one without an id) is always accessible.
     */
    fun isAccessible(exam: ArchiveItem, freeIds: Set<String>, unlockedIds: Set<String>): Boolean {
        val id = exam.path.components.lastOrNull()
        if (exam.level != ArchiveLevel.EXAM || id == null) return true
        return id in freeIds || id in unlockedIds
    }
}
