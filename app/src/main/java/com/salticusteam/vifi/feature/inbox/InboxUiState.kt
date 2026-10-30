package com.salticusteam.vifi.feature.inbox

import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.InboxItem
import com.salticusteam.vifi.core.model.NotificationAction
import com.salticusteam.vifi.core.model.NotificationLocation

/** One inbox card; [isUnread] follows the server's read marker live (the dot goes once the marker echoes back). */
data class InboxEntry(
    val item: InboxItem,
    val isUnread: Boolean,
    /**
     * What the card's button does; `null` without one (no action, or Premium for a subscriber), and then the whole
     * card is inert. With one, the card, its image and the button all do the same.
     */
    val action: NotificationAction? = null,
    /** The image's download URL (loaded with the user's credentials); `null` without an image. */
    val imageUrl: String? = null,
)

data class InboxUiState(
    val items: List<InboxEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    /** One of the two sources failed: its notifications are missing, "Tekrar Dene" restarts both. */
    val hasPartialFailure: Boolean = false,
    /** Notifications are switched off for the app, so new ones arrive silently. */
    val showPermissionOffBanner: Boolean = false,
    /** The tapped push's notification, once it is listed: highlighted and scrolled to. */
    val highlightedId: String? = null,
    /** The exam action being checked: its button shows progress, other exam taps are ignored. */
    val checkingExamId: String? = null,
    /** A locked exam: the archive's unlock sheet is shown for it. */
    val lockedExam: ArchiveItem? = null,
    /** Why an exam could not open, under "Sınav açılamadı". */
    val examError: String? = null,
    /** "Konuma Git": the place whose maps app choice is shown. */
    val locationChoice: NotificationLocation? = null,
)

/** Where a card's action leads, once it may (one-shot). */
sealed interface InboxEvent {
    data class OpenExam(val path: ArchivePath, val kind: ExamKind) : InboxEvent

    data object OpenPaywall : InboxEvent

    /** The add-exam form, pre-filled with [lesson] when an unlock sheet sent the user there. */
    data class OpenAddExam(val lesson: ArchivePath?) : InboxEvent

    /** An https link, opened in the browser. */
    data class OpenUrl(val url: String) : InboxEvent
}
