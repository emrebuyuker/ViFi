package com.salticusteam.vifi.core.model

// Added in 2.5 — an exam the user opened, kept for the home screen's "recently viewed" shelf (iOS RecentExam parity).
/** [openedAt] is epoch milliseconds. */
data class RecentExam(
    val path: ArchivePath,
    val kind: ExamKind,
    val openedAt: Long,
) {
    /** Identity of an entry is its path. */
    val id: ArchivePath get() = path

    val title: String get() = path.displayName.orEmpty()

    /** "University › Lesson", for the card subtitle. */
    val subtitle: String
        get() = listOfNotNull(path.displayNames.firstOrNull(), path.displayNames.dropLast(1).lastOrNull())
            .joinToString(" › ")

    companion object {
        const val MAX_COUNT = 10

        /** Moves [path] to the top, drops any older entry for it and keeps the newest [MAX_COUNT]. */
        fun recorded(list: List<RecentExam>, path: ArchivePath, kind: ExamKind, at: Long): List<RecentExam> =
            (listOf(RecentExam(path, kind, at)) + list.filter { it.path != path }).take(MAX_COUNT)
    }
}
