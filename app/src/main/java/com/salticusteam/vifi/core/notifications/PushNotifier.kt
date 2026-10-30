package com.salticusteam.vifi.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.salticusteam.vifi.R
import com.salticusteam.vifi.app.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a foreground push the way the system tray shows a background one: on the channel of its kind, one tray entry
 * per notification id (under [NotificationContent.TRAY_TAG]), the image as a big picture when it downloads in time,
 * and the notification id on the tap intent. Blocking (the image download); called from FCM's worker thread.
 */
@Singleton
class PushNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: NotificationImageLoader,
) {

    // POST_NOTIFICATIONS is checked below; lint cannot see through the helper.
    /** Posts [content]; false when it was not (no Android 13+ permission: nothing is shown or downloaded). */
    @SuppressLint("MissingPermission")
    fun show(content: NotificationContent): Boolean {
        NotificationChannels.ensure(context)
        // Without the runtime permission (Android 13+) the OS would drop it anyway.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val trayId = content.trayId(anonymousIds::getAndIncrement)
        val picture = content.imageUrl?.let(imageLoader::load)
        val notification = NotificationCompat.Builder(context, content.channelId)
            .setAutoCancel(true)
            .setContentTitle(content.title ?: context.getString(R.string.app_name))
            .setContentText(content.body)
            .setStyle(style(content, picture))
            .setLargeIcon(picture)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentIntent(tapIntent(content, requestCode = trayId))
            .build()
        NotificationManagerCompat.from(context).notify(NotificationContent.TRAY_TAG, trayId, notification)
        return true
    }

    /** The picture when expanded (FCM's own layout: thumbnail collapsed, none expanded), else the full text. */
    private fun style(content: NotificationContent, picture: Bitmap?): NotificationCompat.Style =
        if (picture != null) {
            NotificationCompat.BigPictureStyle().bigPicture(picture).bigLargeIcon(null as Bitmap?)
        } else {
            NotificationCompat.BigTextStyle().bigText(content.body)
        }

    /** A request code per tray entry, so each tap keeps its own notification id (extras do not tell intents apart). */
    private fun tapIntent(content: NotificationContent, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(InboxDeepLink.EXTRA_OPEN_INBOX, true)
        content.tapExtras().forEach { (key, value) -> intent.putExtra(key, value) }
        // FLAG_IMMUTABLE is mandatory from Android 12 (API 31) on.
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        /**
         * Tray ids of pushes without a notification id (older or Console pushes). Seeded with the clock so a later
         * process does not reuse the ids of entries still in the tray.
         */
        val anonymousIds = AtomicInteger((System.currentTimeMillis() / 1000).toInt())
    }
}
