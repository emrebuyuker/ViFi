package com.salticusteam.vifi.feature.inbox

import com.salticusteam.vifi.core.model.Announcement

/** One inbox row; [isUnread] is fixed when the screen opens, so the "new" marks outlive the mark-all-read. */
data class InboxItem(val announcement: Announcement, val isUnread: Boolean)

data class InboxUiState(
    val items: List<InboxItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Notifications are switched off for the app, so new announcements arrive silently. */
    val showPermissionOffBanner: Boolean = false,
)
