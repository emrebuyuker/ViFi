package com.salticusteam.vifi.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.salticusteam.vifi.R
import com.salticusteam.vifi.app.MainActivity

/**
 * Shows FCM data messages as notifications that open the app. Kept from the legacy service; token
 * registration, topics and the notification centre are the notifications area's work (BE-11).
 */
class ViFiMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        showNotification(message.data["message"])
    }

    private fun showNotification(text: String?) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        // Notifications without a channel are silently dropped on Android 8+ (API 26).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_general_name), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val openApp = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // FLAG_IMMUTABLE is mandatory from Android 12 (API 31) on.
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setAutoCancel(true)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(0, notification)
    }

    private companion object {
        const val CHANNEL_ID = "general"
    }
}
