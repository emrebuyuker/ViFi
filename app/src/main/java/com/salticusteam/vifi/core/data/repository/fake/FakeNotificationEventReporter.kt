package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.NotificationEventReporter
import com.salticusteam.vifi.core.model.NotificationEvent

/** Records reports instead of calling `trackNotificationEvent`; [failure] is thrown instead when set. */
class FakeNotificationEventReporter : NotificationEventReporter {

    /** Every successful report, in order, as `event to notificationId`. */
    val reports = mutableListOf<Pair<NotificationEvent, String>>()

    var failure: Exception? = null

    override suspend fun report(event: NotificationEvent, notificationId: String) {
        failure?.let { throw it }
        reports += event to notificationId
    }
}
