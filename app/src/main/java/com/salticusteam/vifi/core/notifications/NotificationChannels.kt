package com.salticusteam.vifi.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.salticusteam.vifi.R

/**
 * The announcements channel, plus the exam-submission channel (background upload progress and outcome). Android 8+ silently drops notifications without a channel, and a push that arrives
 * while the app is in the background is shown by the system using the manifest's `default_notification_channel_id`
 * (this id), so the channel is created at start-up, not only when a message is handled.
 */
object NotificationChannels {
    const val ANNOUNCEMENTS = "announcements"
    const val SUBMISSIONS = "submissions"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            ANNOUNCEMENTS,
            context.getString(R.string.notification_channel_general_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_general_description) }
        manager.createNotificationChannel(channel)
        val submissions = NotificationChannel(
            SUBMISSIONS,
            context.getString(R.string.notification_channel_submissions_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_submissions_description) }
        manager.createNotificationChannel(submissions)
    }
}
