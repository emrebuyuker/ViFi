package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.UnlocksReader

/** Serves a fixed set of unlocked exams for the mock flavor, previews and tests (iOS StubUnlocksReader). */
class FakeUnlocksReader(
    private val ids: Set<String> = emptySet(),
    /** Thrown instead of answering, to exercise the failure path. */
    var failure: Exception? = null,
) : UnlocksReader {

    /** Every uid that was read, in order (for tests). */
    val requestedUids = mutableListOf<String>()

    override suspend fun unlockedExamIds(uid: String): Set<String> {
        requestedUids += uid
        failure?.let { throw it }
        return ids
    }
}
