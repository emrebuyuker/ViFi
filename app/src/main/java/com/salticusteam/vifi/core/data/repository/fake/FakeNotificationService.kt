package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.model.NotificationPermission

/** Notification permission held in memory; [grantOnRequest] decides how the system prompt is answered. */
class FakeNotificationService(
    initial: NotificationPermission = NotificationPermission.AUTHORIZED,
    private val grantOnRequest: Boolean = true,
    /** Whether the system dialog could still be shown; by default only before the first answer. */
    private val canRequest: Boolean = initial == NotificationPermission.NOT_DETERMINED,
) : NotificationService {

    private var current = initial
    val subscribedTopics = mutableSetOf<String>()
    var requestCount = 0
        private set

    /** Every subscribe call, repeated topics included. */
    var subscribeCount = 0
        private set

    /** Thrown by the next subscribe calls while set. */
    var subscribeFailure: Exception? = null

    override suspend fun permission(): NotificationPermission = current

    override suspend fun requestAuthorization(): Boolean {
        requestCount++
        if (current == NotificationPermission.AUTHORIZED) return true
        // Like the system: once it will not ask any more, a request shows nothing and changes nothing.
        if (!canRequest) return false
        current = if (grantOnRequest) NotificationPermission.AUTHORIZED else NotificationPermission.DENIED
        return grantOnRequest
    }

    override suspend fun canRequestPermission(): Boolean =
        canRequest && current != NotificationPermission.AUTHORIZED

    override suspend fun token(): String? = "fake-token"

    override suspend fun subscribe(topic: String) {
        subscribeCount++
        subscribeFailure?.let { throw it }
        subscribedTopics += topic
    }

    override suspend fun unsubscribe(topic: String) {
        subscribedTopics -= topic
    }
}
