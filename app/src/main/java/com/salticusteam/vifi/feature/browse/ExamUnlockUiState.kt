package com.salticusteam.vifi.feature.browse

import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamLock

/**
 * Coin lock state of an archive list: the live balance, the user's unlocked exams and the unlock sheet
 * ([examToUnlock] is non-null while it is shown).
 */
data class ExamUnlockUiState(
    val balance: Int = 0,
    val unlockedIds: Set<String> = emptySet(),
    val examToUnlock: ArchiveItem? = null,
    val isUnlocking: Boolean = false,
    /** The last unlock failure, shown in the sheet. */
    val error: String? = null,
) {
    val canAfford: Boolean get() = balance >= ExamLock.UNLOCK_COST

    /**
     * The paths of the exams in [items] shown locked. The free ones are computed from the full loaded list —
     * never the search-filtered one — so filtering cannot change which exams are free (iOS `BrowseView`).
     */
    fun lockedPaths(items: List<ArchiveItem>?): Set<ArchivePath> {
        if (items.isNullOrEmpty()) return emptySet()
        val freeIds = ExamLock.freeExamIds(items)
        return items.filter { ExamLock.isLocked(it, freeIds, unlockedIds) }.mapTo(mutableSetOf()) { it.path }
    }
}

/** One-off results of the unlock flow. */
sealed interface ExamUnlockEvent {
    /** The exam was unlocked (or already was): the only continuation is opening it. */
    data class Unlocked(val exam: ArchiveItem) : ExamUnlockEvent

    /** The user chose to earn coins (the add-exam flow); [lesson] is the locked exam's lesson, for the pre-fill. */
    data class EarnCoins(val lesson: ArchivePath?) : ExamUnlockEvent
}
