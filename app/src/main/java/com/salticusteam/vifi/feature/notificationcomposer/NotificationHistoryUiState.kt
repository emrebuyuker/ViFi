package com.salticusteam.vifi.feature.notificationcomposer

import com.salticusteam.vifi.core.model.NotificationStatus
import com.salticusteam.vifi.core.model.SentNotification

/**
 * The admin's sending history (iOS `NotificationHistoryViewModel`'s state). [notifications] is `null` until the first
 * successful load, so a failed refresh keeps the rows on screen (with [errorMessage] above them) while an empty
 * screen shows [loadError] instead.
 */
data class NotificationHistoryUiState(
    val notifications: List<SentNotification>? = null,
    /** The first load failed; shown full screen with a retry. */
    val loadError: String? = null,
    /** A refresh or a cancellation failed; shown above the rows. */
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false,
    /** The notification whose cancellation is in flight; blocks a second one. */
    val cancelingId: String? = null,
) {
    val isLoading: Boolean get() = notifications == null && loadError == null

    /** Only a scheduled notification can be canceled, and one at a time. */
    fun canCancel(notification: SentNotification): Boolean =
        cancelingId == null && notification.status == NotificationStatus.SCHEDULED
}
