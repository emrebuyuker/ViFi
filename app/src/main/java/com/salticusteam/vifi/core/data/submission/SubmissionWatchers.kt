package com.salticusteam.vifi.core.data.submission

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** The drafts whose form is currently observing its upload (see [SubmissionQueue.status]). */
@Singleton
class SubmissionWatchers @Inject constructor() {

    private val counts = ConcurrentHashMap<String, Int>()

    fun watch(draftId: String) {
        counts.merge(draftId, 1, Int::plus)
    }

    fun unwatch(draftId: String) {
        counts.computeIfPresent(draftId) { _, count -> (count - 1).takeIf { it > 0 } }
    }

    fun isWatched(draftId: String): Boolean = counts.containsKey(draftId)
}
