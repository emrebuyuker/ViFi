package com.salticusteam.vifi.feature.inbox

import com.salticusteam.vifi.core.model.InboxItem

/** One inbox row; [isUnread] follows the server's read marker live (the dot goes once the marker echoes back). */
data class InboxEntry(val item: InboxItem, val isUnread: Boolean)

data class InboxUiState(
    val items: List<InboxEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Notifications are switched off for the app, so new ones arrive silently. */
    val showPermissionOffBanner: Boolean = false,
)
