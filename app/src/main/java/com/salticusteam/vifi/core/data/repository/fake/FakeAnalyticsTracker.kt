package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsUser

/** Records what was reported instead of sending it (mock flavor, previews and tests). */
class FakeAnalyticsTracker : AnalyticsTracker {

    private val _events = mutableListOf<AnalyticsEvent>()
    private val _users = mutableListOf<AnalyticsUser?>()

    /** Every event reported so far, in order. */
    val events: List<AnalyticsEvent> get() = _events.toList()

    /** Every identity set so far, in order (`null` is a clear). */
    val users: List<AnalyticsUser?> get() = _users.toList()

    /** The identity currently set, or `null` when none or cleared. */
    val currentUser: AnalyticsUser? get() = _users.lastOrNull()

    override fun track(event: AnalyticsEvent) {
        _events += event
    }

    override fun setUser(user: AnalyticsUser?) {
        _users += user
    }
}
