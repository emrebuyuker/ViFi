package com.salticusteam.vifi.core.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clears the launcher icon badge (iOS `RootView.clearNotificationBadge`, N9). On Android the badge (dot or count)
 * is derived from the app's notifications still in the tray, so clearing it means removing them.
 *
 * Pushes are shown either by [ViFiMessagingService] (foreground) or by the system itself (background, under an
 * FCM-generated tag the app cannot address one by one), hence `cancelAll()`. That also clears the local exam
 * submission notifications ([SubmissionNotifier]), which NDEC-04 accepts. Phone verification's pushes are silent and
 * never reach the tray. The pushes stay readable in the inbox. The badge itself is the system's (channels show it, no
 * count is set).
 */
@Singleton
class NotificationBadge @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun clear() {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
