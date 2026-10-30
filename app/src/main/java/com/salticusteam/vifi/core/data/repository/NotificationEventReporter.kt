package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.NotificationEvent

/**
 * Tells the server that the signed-in user opened or acted on a notification, so the admin history can count it
 * (iOS `NotificationEventReporting`). The live implementation calls the `trackNotificationEvent` Cloud Function,
 * which counts once per user and event.
 */
interface NotificationEventReporter {

    /** Reports [event] for [notificationId]. Throws when the report did not reach the server. */
    suspend fun report(event: NotificationEvent, notificationId: String)
}
