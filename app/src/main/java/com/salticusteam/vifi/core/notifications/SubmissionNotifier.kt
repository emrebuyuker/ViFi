package com.salticusteam.vifi.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ForegroundInfo
import com.salticusteam.vifi.R
import com.salticusteam.vifi.app.MainActivity
import com.salticusteam.vifi.core.data.submission.SubmissionWatchers
import com.salticusteam.vifi.core.model.ExamSubmissionError
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The notifications of background exam submissions: the outcome when the user is not looking at the form (it
 * reports the outcome itself while on screen), and the ongoing "sending" notification an expedited upload needs
 * as a foreground service below Android 12. Tapping one opens the app.
 */
@Singleton
class SubmissionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchers: SubmissionWatchers,
) {

    suspend fun notifySent(draftId: String) {
        if (isShownOnScreen(draftId)) return
        show(
            draftId,
            context.getString(R.string.submission_notification_sent_title),
            context.getString(R.string.submission_notification_sent_message),
        )
    }

    suspend fun notifyFailed(draftId: String, error: ExamSubmissionError) {
        if (isShownOnScreen(draftId)) return
        show(draftId, context.getString(R.string.submission_notification_failed_title), error.userMessage)
    }

    fun progressForegroundInfo(): ForegroundInfo {
        NotificationChannels.ensure(context)
        val notification = builder(context.getString(R.string.submission_notification_progress_title))
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        return ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
    }

    // Helpers
    /** Whether the draft's form observes the upload while the app is in the foreground. */
    private suspend fun isShownOnScreen(draftId: String): Boolean {
        if (!watchers.isWatched(draftId)) return false
        return withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
    }

    // POST_NOTIFICATIONS is checked below; lint cannot see through the helper.
    @SuppressLint("MissingPermission")
    private fun show(draftId: String, title: String, message: String) {
        // Without the runtime permission (Android 13+) the OS would drop it anyway.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationChannels.ensure(context)
        val notification = builder(title)
            .setAutoCancel(true)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()
        NotificationManagerCompat.from(context).notify(draftId.hashCode(), notification)
    }

    private fun builder(title: String): NotificationCompat.Builder {
        val openApp = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // FLAG_IMMUTABLE is mandatory from Android 12 (API 31) on.
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, NotificationChannels.SUBMISSIONS)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_STATUS)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentIntent(pendingIntent)
    }

    private companion object {
        /** Distinct from the announcement notification (id 0) and the per-draft outcome ids. */
        const val PROGRESS_NOTIFICATION_ID = 0x5ab
    }
}
