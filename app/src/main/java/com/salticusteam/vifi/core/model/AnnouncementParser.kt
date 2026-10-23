package com.salticusteam.vifi.core.model

import java.time.Instant
import java.util.Date

// Added in 2.5 — turns an `announcements` document into the domain model, free of Firebase types so it is unit-testable.
object AnnouncementParser {

    object Field {
        const val TITLE = "title"
        const val BODY = "body"
        const val CREATED_AT = "createdAt"
    }

    /**
     * One document, or `null` when it has no usable text. [createdAt] arrives as an [Instant], a [Date] or epoch
     * milliseconds (the live reader converts Firestore's Timestamp first); anything else leaves the date unknown.
     */
    fun parse(id: String, data: Map<String, Any?>): Announcement? {
        val title = (data[Field.TITLE] as? String)?.trim().orEmpty()
        val body = (data[Field.BODY] as? String)?.trim().orEmpty()
        if (title.isEmpty() && body.isEmpty()) return null
        return Announcement(id = id, title = title, body = body, createdAt = instant(data[Field.CREATED_AT]))
    }

    /** Newest first; announcements without a date go last, ties keep a stable order by id. */
    fun newestFirst(items: List<Announcement>): List<Announcement> =
        items.sortedWith(compareByDescending<Announcement> { it.createdAt ?: Instant.MIN }.thenBy { it.id })

    private fun instant(value: Any?): Instant? = when (value) {
        is Instant -> value
        is Date -> value.toInstant()
        is Long -> Instant.ofEpochMilli(value)
        else -> null
    }
}
