package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.functions.FirebaseFunctions
import com.salticusteam.vifi.core.data.repository.NotificationEventReporter
import com.salticusteam.vifi.core.model.NotificationEvent
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/**
 * Calls the shared `trackNotificationEvent` Cloud Function (europe-west1; iOS `FirebaseNotificationEventReporter`).
 * The server counts once per user and event, and only for a notification this user received; the reply
 * (`{counted}`) is not needed.
 *
 * Added in 4.1.0 — `{notificationId, event: "opened" | "actionTapped"}`.
 */
class FirebaseNotificationEventReporter @Inject constructor(
    private val functions: FirebaseFunctions,
) : NotificationEventReporter {

    override suspend fun report(event: NotificationEvent, notificationId: String) {
        functions.getHttpsCallable(FUNCTION_NAME)
            .call(mapOf("notificationId" to notificationId, "event" to event.raw))
            .await()
    }

    private companion object {
        const val FUNCTION_NAME = "trackNotificationEvent"
    }
}
