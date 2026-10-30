package com.salticusteam.vifi.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.salticusteam.vifi.R
import com.salticusteam.vifi.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

/**
 * Receives FCM pushes. The push only informs: the announcement itself lives in Firestore and the inbox, so a tap
 * opens the app on the inbox (iOS parity). In the background the system shows "notification" messages itself
 * (channel, icon and colour come from the manifest defaults); this service shows foreground and data-only pushes.
 */
@AndroidEntryPoint
class ViFiMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushTokenRegistration: PushTokenRegistration

    override fun onMessageReceived(message: RemoteMessage) {
        val content = NotificationContent.from(
            notificationTitle = message.notification?.title,
            notificationBody = message.notification?.body,
            data = message.data,
        )
        if (!content.isEmpty) show(content)
    }

    /** Re-subscribe so pushes to `all` keep arriving, and store the token for the signed-in user (N11). */
    override fun onNewToken(token: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(PushTopics.ALL)
            .addOnFailureListener { Timber.w(it, "Subscribing to the all topic failed") }
        pushTokenRegistration.onNewToken(token)
    }

    // POST_NOTIFICATIONS is checked below; lint cannot see through the helper.
    @SuppressLint("MissingPermission")
    private fun show(content: NotificationContent) {
        NotificationChannels.ensure(this)
        val manager = NotificationManagerCompat.from(this)
        // Without the runtime permission (Android 13+) the OS would drop it anyway.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val openInbox = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(InboxDeepLink.EXTRA_OPEN_INBOX, true)
        // FLAG_IMMUTABLE is mandatory from Android 12 (API 31) on.
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openInbox, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, NotificationChannels.GENERAL)
            .setAutoCancel(true)
            .setContentTitle(content.title ?: getString(R.string.app_name))
            .setContentText(content.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setColor(ContextCompat.getColor(this, R.color.notification_accent))
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val NOTIFICATION_ID = 0
    }
}

/** Topics every install joins (the Console targets `all` for announcements). */
object PushTopics {
    const val ALL = "all"
}
