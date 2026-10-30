package com.salticusteam.vifi.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import com.salticusteam.vifi.R

/**
 * The app's notification channels: one per notification kind (the server's push names it as
 * `android.notification.channelId` = `kind`), plus the exam-submission channel (background upload progress and
 * outcome). Android 8+ silently drops notifications without a channel, and a push that arrives while the app is in
 * the background is shown by the system on the channel it names or, if that one is missing, on the manifest's
 * `default_notification_channel_id` ([GENERAL]); so the channels are created at start-up, not only when a message
 * is handled.
 *
 * A channel id can never change once created (only its name and description can), and the user may switch a channel
 * off in system settings. Switching `promo` off there is Android's own path: it does not touch the server-side
 * campaign consent, so the push stops but the campaign still shows in the inbox. The badge is the system's own
 * (NDEC-04): every channel shows it, no count is set.
 */
object NotificationChannels {
    // Added in 4.1.0 — announcements; the manifest's default channel. Equals `NotificationKind.GENERAL.raw`.
    const val GENERAL = "general"

    // Added in 4.1.0 — campaigns, delivered only with marketing consent. Equals `NotificationKind.PROMO.raw`.
    const val PROMO = "promo"

    // Added in 4.1.0 — automatic messages about the user's own exams and account. Equals `NotificationKind.SYSTEM.raw`.
    const val SYSTEM = "system"

    const val SUBMISSIONS = "submissions"

    /** Channels of earlier versions, deleted on start-up: 4.0.0's `announcements` became [GENERAL]. */
    internal val RETIRED = listOf("announcements")

    internal data class Spec(val id: String, @StringRes val name: Int, @StringRes val description: Int)

    internal val SPECS = listOf(
        Spec(GENERAL, R.string.notification_channel_general_name, R.string.notification_channel_general_description),
        Spec(PROMO, R.string.notification_channel_promo_name, R.string.notification_channel_promo_description),
        Spec(SYSTEM, R.string.notification_channel_system_name, R.string.notification_channel_system_description),
        Spec(
            SUBMISSIONS,
            R.string.notification_channel_submissions_name,
            R.string.notification_channel_submissions_description,
        ),
    )

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // A no-op once deleted; deleting also removes whatever that channel still had in the tray.
        RETIRED.forEach(manager::deleteNotificationChannel)
        val channels = SPECS.map { spec ->
            NotificationChannel(spec.id, context.getString(spec.name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(spec.description)
                setShowBadge(true)
            }
        }
        // Re-creating an existing channel only refreshes its name and description; the user's choices stay.
        manager.createNotificationChannels(channels)
    }
}
