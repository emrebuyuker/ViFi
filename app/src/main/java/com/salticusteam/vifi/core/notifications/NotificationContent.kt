package com.salticusteam.vifi.core.notifications

import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationPayloadParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * What a foreground push shows and where its tap leads, read from the FCM message (the server sends
 * `notification{title, body, imageUrl?}` and `data{notificationId, kind}`). The Console "notification" part wins for
 * the text; the data keys `title`/`body`/`message` are the fallback of older pushes.
 */
data class NotificationContent(
    val title: String?,
    val body: String?,

    // Added in 4.1.0 — the inbox item a tap opens and highlights; null for pushes sent before 4.1.0.
    val notificationId: String? = null,

    // Added in 4.1.0 — picks the channel (the server names it `channelId = kind`); null when missing or unknown.
    val kind: NotificationKind? = null,

    // Added in 4.1.0 — the push's image, a 7-day signed Storage URL minted at delivery; https only.
    val imageUrl: String? = null,
) {

    /** A push with neither title nor text is not worth a notification. */
    val isEmpty: Boolean get() = title.isNullOrBlank() && body.isNullOrBlank()

    /** The channel of the push's kind; a missing or unknown kind (an older or a Console push) is an announcement. */
    val channelId: String
        get() = when (kind) {
            NotificationKind.PROMO -> NotificationChannels.PROMO
            NotificationKind.SYSTEM -> NotificationChannels.SYSTEM
            NotificationKind.GENERAL, null -> NotificationChannels.GENERAL
        }

    /** One tray entry per notification: a push only replaces an earlier copy of itself, never another one. */
    fun trayId(nextAnonymousId: () -> Int): Int = notificationId?.hashCode() ?: nextAnonymousId()

    /**
     * The string extras of the tap intent: the same keys the system tray's own launch intent carries for a
     * background push, so both taps are read alike (PSH-03).
     */
    fun tapExtras(): Map<String, String> = buildMap {
        notificationId?.let { put(EXTRA_NOTIFICATION_ID, it) }
        kind?.let { put(EXTRA_KIND, it.raw) }
    }

    companion object {
        /** The FCM data keys, reused as the tap intent's extras. */
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val EXTRA_KIND = "kind"

        /** Tags the app's pushes in the tray, apart from the exam submission notifications' own ids. */
        const val TRAY_TAG = "vifi.push"

        fun from(
            notificationTitle: String?,
            notificationBody: String?,
            notificationImageUrl: String?,
            data: Map<String, String>,
        ): NotificationContent =
            NotificationContent(
                title = (notificationTitle ?: data["title"])?.takeIf { it.isNotBlank() },
                body = (notificationBody ?: data["body"] ?: data["message"])?.takeIf { it.isNotBlank() },
                notificationId = NotificationPayloadParser.notificationIdFromPush(data),
                kind = NotificationKind.fromRaw(data[EXTRA_KIND]),
                // Kept verbatim (a signed URL must not be re-encoded); only a well-formed https URL is accepted.
                imageUrl = notificationImageUrl?.trim()?.takeIf { it.toHttpUrlOrNull()?.isHttps == true },
            )
    }
}
