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
 * Every visible notification the app posts is an announcement push, shown either by [ViFiMessagingService]
 * (foreground) or by the system itself (background, under an FCM-generated tag the app cannot address one by
 * one), hence `cancelAll()`. Phone verification's pushes are silent and never reach the tray, so nothing else is
 * lost. The announcements stay readable in the inbox.
 */
@Singleton
class NotificationBadge @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun clear() {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
