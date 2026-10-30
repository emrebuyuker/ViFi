package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.model.NotificationPermission

/** Notification permission held in memory; [grantOnRequest] decides how the prompt is answered. */
class FakeNotificationService(
    initial: NotificationPermission = NotificationPermission.AUTHORIZED,
    private val grantOnRequest: Boolean = true,
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
        current = if (grantOnRequest) NotificationPermission.AUTHORIZED else NotificationPermission.DENIED
        return grantOnRequest
    }

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
