package com.salticusteam.vifi.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives FCM pushes. The push only informs: the notification itself lives in Firestore and the inbox, so a tap
 * opens the inbox on that item (iOS parity).
 *
 * In the background the system shows a push itself, on the channel the server names (`channelId = kind`) with the
 * image FCM downloads, and its tap launches the app with the data keys as extras. In the foreground FCM hands the
 * push here and [PushNotifier] posts it the same way.
 */
@AndroidEntryPoint
class ViFiMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushNotifier: PushNotifier

    @Inject lateinit var pushTokenRegistration: PushTokenRegistration

    @Inject lateinit var pushTopicSubscription: PushTopicSubscription

    // Runs on FCM's worker thread, so the image may be downloaded right here.
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification
        val content = NotificationContent.from(
            notificationTitle = notification?.title,
            notificationBody = notification?.body,
            notificationImageUrl = notification?.imageUrl?.toString(),
            data = message.data,
        )
        if (!content.isEmpty) pushNotifier.show(content)
    }

    /** Re-join `all` (a new token has no topics) and store the token for the signed-in user (N11). */
    override fun onNewToken(token: String) {
        pushTopicSubscription.refresh()
        pushTokenRegistration.onNewToken(token)
    }
}
