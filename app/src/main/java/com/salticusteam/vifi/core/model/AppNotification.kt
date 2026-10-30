package com.salticusteam.vifi.core.model

import java.time.Instant

// The notification contract shared by the inbox, the admin composer and the server (iOS `AppNotification.swift`).
// Wire values (raw values, action `type`s, limits) must stay in step with the shared backend's
// `firebase/functions/notifications/schema.js`, which is the authority.

// Added in 4.1.0 — what a notification is for; the raw value is the backend contract and the Android channel id.
/** Decides who may receive it: `promo` reaches only users who opted in to campaigns (server-side filter). */
enum class NotificationKind(val raw: String) {
    /** An announcement: maintenance, a new feature, news. */
    GENERAL("general"),

    /** A campaign or advertisement; delivered only to users who opted in to marketing notifications. */
    PROMO("promo"),

    /** An automatic message about the user's own activity (coins credited, submission rejected, new exam). */
    SYSTEM("system");

    companion object {
        fun fromRaw(raw: String?): NotificationKind? = entries.firstOrNull { it.raw == raw }
    }
}

// Added in 4.1.0 — a place attached to a campaign, opened in a maps app from the inbox.
data class NotificationLocation(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val address: String? = null,
    /** The Google Places id, when the place was picked from a search result or a map label. */
    val placeId: String? = null,
) {
    /** In range, with a name; lengths are counted in code points like the server. */
    val isValid: Boolean
        get() {
            val trimmedName = name.trim()
            return latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
                trimmedName.isNotEmpty() && trimmedName.codePointLength() <= NAME_LIMIT &&
                (address?.codePointLength() ?: 0) <= ADDRESS_LIMIT
        }

    companion object {
        const val NAME_LIMIT = 120
        const val ADDRESS_LIMIT = 200
    }
}

// Added in 4.1.0 — an exam a notification links to, resolved server-side from `exams/{examId}`.
/** [path] is `[universityId, facultyId, departmentId, courseId, examId]` with the display names. */
data class NotificationExamTarget(
    val examId: String,
    val path: ArchivePath,
    val kind: ExamKind,
)

// Added in 4.1.0 — the single action a notification offers: its inbox button, and what tapping the card does.
sealed interface NotificationAction {
    /** The `type` value on the wire. */
    val type: String

    data class Location(val location: NotificationLocation) : NotificationAction {
        override val type: String get() = "location"
    }

    data class Exam(val target: NotificationExamTarget) : NotificationAction {
        override val type: String get() = "exam"
    }

    data object Paywall : NotificationAction {
        override val type: String get() = "paywall"
    }

    data object AddExam : NotificationAction {
        override val type: String get() = "addExam"
    }

    /** An external link; always `https` with a host (validated on parse). */
    data class Url(val url: String) : NotificationAction {
        override val type: String get() = "url"
    }
}

// Added in 4.1.0 — where an inbox entry was delivered.
enum class InboxSource {
    /** `announcements/{id}`: sent to everyone. */
    BROADCAST,

    /** `users/{uid}/inbox/{id}`: sent to this user (a segment, a campaign or an automatic message). */
    PERSONAL,
}

// Added in 4.1.0 — one entry of the in-app inbox, from the public broadcast list or the user's own inbox.
data class InboxItem(
    /** The `notificationId`, the same in both sources (used for highlighting and statistics). */
    val id: String,
    val source: InboxSource,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    /**
     * The campaign image's Storage path (`notificationImages/<id>.jpg`), downloaded with the user's credentials;
     * `null` without an image. Images are never public.
     */
    val imageStoragePath: String? = null,
    val action: NotificationAction? = null,
    val createdAt: Instant? = null,
    val expiresAt: Instant? = null,
) {
    /** Past its validity and no longer shown; the expiry instant itself counts as expired (iOS parity). */
    fun isExpired(now: Instant): Boolean = expiresAt?.let { !it.isAfter(now) } ?: false
}

// Added in 4.1.0 — a notification's campaign image: one JPEG per notification (storage.rules, schema.js).
object NotificationImage {
    const val FOLDER = "notificationImages"

    /** The Storage rule's (and the server's) size cap. */
    const val MAX_BYTES = 1024 * 1024

    /** The composer downsamples the longest side to this before encoding. */
    const val MAX_PIXEL_SIZE = 1600

    private const val SUFFIX = ".jpg"

    /** Where the image of notification [notificationId] is uploaded. */
    fun storagePath(notificationId: String): String = "$FOLDER/$notificationId$SUFFIX"

    /** Whether [path] is a notification image path: `notificationImages/<notification id>.jpg`. */
    fun isValidStoragePath(path: String): Boolean {
        val prefix = "$FOLDER/"
        if (!path.startsWith(prefix) || !path.endsWith(SUFFIX)) return false
        val name = path.substring(prefix.length, path.length - SUFFIX.length)
        return NotificationPayloadParser.isValidNotificationId(name)
    }
}

// Added in 4.1.0 — a slice of the registered users, matched against their profile's canonical ids.
data class NotificationSegment(
    val universityId: String,
    /** Narrows to one faculty of the university. */
    val facultyId: String? = null,
    /** Narrows to one department of the faculty; requires [facultyId]. */
    val departmentId: String? = null,
    /** `null` sends to both. */
    val gender: Gender? = null,
)

// Added in 4.1.0 — who a notification goes to; the scope is the backend contract.
sealed interface NotificationAudience {
    /** The `scope` value on the wire. */
    val scope: String

    /** Every user (and every installed app subscribed to the broadcast topic). */
    data object All : NotificationAudience {
        override val scope: String get() = "all"
    }

    data class Segment(val segment: NotificationSegment) : NotificationAudience {
        override val scope: String get() = "segment"
    }

    /** One user; automatic notifications only (the admin composer cannot send it). */
    data class User(val uid: String) : NotificationAudience {
        override val scope: String get() = "user"
    }

    /** Every admin; automatic notifications only. */
    data object Admins : NotificationAudience {
        override val scope: String get() = "admins"
    }
}

// Added in 4.1.0 — what a user did with a notification; the raw values are `trackNotificationEvent`'s event names.
enum class NotificationEvent(val raw: String) {
    /** The inbox was opened from this notification's push. */
    OPENED("opened"),

    /** The notification's button, or its card, was used. */
    ACTION_TAPPED("actionTapped"),
}

/** Length in Unicode code points, which is how the server (`[...text].length`) counts the contract's limits. */
internal fun String.codePointLength(): Int = codePointCount(0, length)
