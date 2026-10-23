package com.salticusteam.vifi.core.notifications

/** What a push shows: the Console "notification" part wins, the data keys `title`/`body`/`message` are the fallback. */
data class NotificationContent(val title: String?, val body: String?) {

    /** A push with neither title nor text is not worth a notification. */
    val isEmpty: Boolean get() = title.isNullOrBlank() && body.isNullOrBlank()

    companion object {
        fun from(notificationTitle: String?, notificationBody: String?, data: Map<String, String>): NotificationContent =
            NotificationContent(
                title = (notificationTitle ?: data["title"])?.takeIf { it.isNotBlank() },
                body = (notificationBody ?: data["body"] ?: data["message"])?.takeIf { it.isNotBlank() },
            )
    }
}
