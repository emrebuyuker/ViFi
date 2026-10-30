package com.salticusteam.vifi.core.model

import java.net.MalformedURLException
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.UUID

// Added in 4.1.0 — a notification an admin is composing ("Bildirim Gönder"); iOS `NotificationDraft`.
/**
 * Validated here and again by the server (`sendNotification`), which is the authority. Lengths are counted in code
 * points, like the server. The Turkish text of each [Issue] lives in the composer's string resources.
 */
data class NotificationDraft(
    /** The server-side `notificationId`, generated once per draft so a retried send is not delivered twice. */
    val id: String = newId(),
    val kind: NotificationKind = NotificationKind.GENERAL,
    val title: String = "",
    val body: String = "",
    val action: Action? = null,
    /**
     * Storage path of the uploaded campaign image: always this draft's own [NotificationImage.storagePath] (the
     * server refuses any other), set once the upload has finished.
     */
    val imagePath: String? = null,
    val audience: NotificationAudience = NotificationAudience.All,
    /** `null` sends now (campaigns are still held over quiet hours by the server). */
    val scheduledAt: Instant? = null,
    val expiresAt: Instant? = null,
    /** Sends only to the composing admin, to see it on a real device. */
    val isTest: Boolean = false,
) {

    /** What the admin attaches; the exam is given by id and resolved server-side. */
    sealed interface Action {
        data class Location(val location: NotificationLocation) : Action
        data class Exam(val examId: String) : Action
        data object Paywall : Action
        data object AddExam : Action

        /** The link as typed (trimmed); must be `https` with a host. */
        data class Url(val url: String) : Action
    }

    /** Why a draft cannot be sent yet, in the order [validationIssue] checks them. */
    enum class Issue {
        EMPTY_TITLE,
        TITLE_TOO_LONG,
        EMPTY_BODY,
        BODY_TOO_LONG,
        INVALID_AUDIENCE,
        INVALID_LOCATION,
        INVALID_URL,
        EMPTY_EXAM,
        INVALID_IMAGE,
        SCHEDULE_IN_PAST,
        EXPIRY_BEFORE_DELIVERY,
    }

    /** The kind actually sent: a location is always advertising, so it always goes as `promo`. */
    val effectiveKind: NotificationKind
        get() = if (action is Action.Location) NotificationKind.PROMO else kind

    val trimmedTitle: String get() = title.trim()

    val trimmedBody: String get() = body.trim()

    /** The first problem that blocks sending, or `null` when the draft is ready. */
    fun validationIssue(now: Instant = Instant.now()): Issue? {
        if (trimmedTitle.isEmpty()) return Issue.EMPTY_TITLE
        if (trimmedTitle.codePointLength() > TITLE_LIMIT) return Issue.TITLE_TOO_LONG
        if (trimmedBody.isEmpty()) return Issue.EMPTY_BODY
        if (trimmedBody.codePointLength() > BODY_LIMIT) return Issue.BODY_TOO_LONG
        if (!isSendable(audience)) return Issue.INVALID_AUDIENCE
        actionIssue(action)?.let { return it }
        if (imagePath != null && imagePath != NotificationImage.storagePath(id)) return Issue.INVALID_IMAGE
        // A minute of grace, so a time picked "now" is not rejected by the time the admin taps send.
        if (scheduledAt != null && scheduledAt.isBefore(now.minus(SCHEDULE_GRACE))) return Issue.SCHEDULE_IN_PAST
        if (expiresAt != null && !expiresAt.isAfter(scheduledAt ?: now)) return Issue.EXPIRY_BEFORE_DELIVERY
        return null
    }

    /** The `sendNotification` callable's input. Dates are epoch milliseconds. */
    fun payload(): Map<String, Any> = buildMap {
        put("notificationId", id)
        put("kind", effectiveKind.raw)
        put("title", trimmedTitle)
        put("body", trimmedBody)
        put("audience", audiencePayload(audience))
        put("isTest", isTest)
        action?.let { put("action", actionPayload(it)) }
        imagePath?.let { put("imagePath", it) }
        scheduledAt?.let { put("scheduledAt", it.toEpochMilli()) }
        expiresAt?.let { put("expiresAt", it.toEpochMilli()) }
    }

    companion object {
        const val TITLE_LIMIT = 80
        const val BODY_LIMIT = 500
        const val URL_LIMIT = 500

        private val SCHEDULE_GRACE: Duration = Duration.ofSeconds(60)

        /** A lowercase UUID, which the server accepts as a document id (`^[A-Za-z0-9_-]{1,128}$`). */
        fun newId(): String = UUID.randomUUID().toString().lowercase()

        /** Whether [text] is an `https` link with a host, within [URL_LIMIT]. */
        fun isHttpsUrl(text: String): Boolean {
            if (text.isEmpty() || text.length > URL_LIMIT) return false
            val url = try {
                URL(text)
            } catch (_: MalformedURLException) {
                return false
            }
            return url.protocol.equals("https", ignoreCase = true) && !url.host.isNullOrEmpty()
        }

        private fun isSendable(audience: NotificationAudience): Boolean = when (audience) {
            NotificationAudience.All -> true
            is NotificationAudience.Segment -> {
                val segment = audience.segment
                val hasFaculty = !segment.facultyId.isNullOrEmpty()
                val hasDepartment = !segment.departmentId.isNullOrEmpty()
                segment.universityId.isNotEmpty() && (hasFaculty || !hasDepartment)
            }
            // Server-only audiences.
            is NotificationAudience.User, NotificationAudience.Admins -> false
        }

        private fun actionIssue(action: Action?): Issue? = when (action) {
            is Action.Location -> if (action.location.isValid) null else Issue.INVALID_LOCATION
            is Action.Exam -> if (action.examId.isBlank()) Issue.EMPTY_EXAM else null
            is Action.Url -> if (isHttpsUrl(action.url)) null else Issue.INVALID_URL
            Action.Paywall, Action.AddExam, null -> null
        }

        private fun audiencePayload(audience: NotificationAudience): Map<String, Any> = buildMap {
            put("scope", audience.scope)
            when (audience) {
                is NotificationAudience.Segment -> {
                    val segment = audience.segment
                    put("universityId", segment.universityId)
                    segment.facultyId?.takeIf { it.isNotEmpty() }?.let { put("facultyId", it) }
                    segment.departmentId?.takeIf { it.isNotEmpty() }?.let { put("departmentId", it) }
                    segment.gender?.let { put("gender", it.raw) }
                }
                is NotificationAudience.User -> put("uid", audience.uid)
                NotificationAudience.All, NotificationAudience.Admins -> Unit
            }
        }

        private fun actionPayload(action: Action): Map<String, Any> = when (action) {
            is Action.Location -> buildMap {
                val location = action.location
                put("type", "location")
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("name", location.name.trim())
                location.address?.takeIf { it.isNotEmpty() }?.let { put("address", it) }
                location.placeId?.takeIf { it.isNotEmpty() }?.let { put("placeId", it) }
            }
            is Action.Exam -> mapOf("type" to "exam", "examId" to action.examId.trim())
            Action.Paywall -> mapOf("type" to "paywall")
            Action.AddExam -> mapOf("type" to "addExam")
            is Action.Url -> mapOf("type" to "url", "url" to action.url)
        }
    }
}

// Added in 4.1.0 — how many registered users a node of the audience tree has, in total and per gender.
data class AudienceCounts(val total: Int, val female: Int, val male: Int) {
    /** The users a gender filter reaches (`null` = both, which also counts users with no gender recorded). */
    fun count(gender: Gender?): Int = when (gender) {
        Gender.FEMALE -> female
        Gender.MALE -> male
        null -> total
    }
}

// Added in 4.1.0 — a university, faculty or department that at least one registered user has in their profile.
data class AudienceNode(
    /** The profile's canonical id (slug). */
    val id: String,
    val name: String,
    val counts: AudienceCounts,
    /** Faculties under a university, departments under a faculty; empty for a department. */
    val children: List<AudienceNode> = emptyList(),
)

// Added in 4.1.0 — the audience pickers' data (`getNotificationAudience`): names and counts only, no personal data.
data class AudienceFacets(
    /** Users with a completed profile. */
    val total: Int,
    val universities: List<AudienceNode>,
) {
    fun faculties(universityId: String): List<AudienceNode> =
        universities.firstOrNull { it.id == universityId }?.children.orEmpty()

    fun departments(universityId: String, facultyId: String): List<AudienceNode> =
        faculties(universityId).firstOrNull { it.id == facultyId }?.children.orEmpty()

    /**
     * The users a segment reaches according to these counts. An estimate: campaigns are further narrowed to users
     * who opted in, which only `previewNotification` knows.
     */
    fun estimatedRecipients(segment: NotificationSegment): Int {
        val university = universities.firstOrNull { it.id == segment.universityId } ?: return 0
        val facultyId = segment.facultyId?.takeIf { it.isNotEmpty() } ?: return university.counts.count(segment.gender)
        val faculty = university.children.firstOrNull { it.id == facultyId } ?: return 0
        val departmentId = segment.departmentId?.takeIf { it.isNotEmpty() } ?: return faculty.counts.count(segment.gender)
        return faculty.children.firstOrNull { it.id == departmentId }?.counts?.count(segment.gender) ?: 0
    }
}

// Added in 4.1.0 — the exact reach of a notification before sending (`previewNotification`).
data class NotificationPreview(
    /** Users who will see it in their inbox (campaigns: only those who opted in). */
    val recipients: Int,
    /** Registered devices a push will be sent to. */
    val pushTargets: Int,
)

// Added in 4.1.0 — where a notification is in its delivery; the raw value is the backend contract.
enum class NotificationStatus(val raw: String) {
    SCHEDULED("scheduled"),
    SENDING("sending"),
    SENT("sent"),
    FAILED("failed"),
    CANCELED("canceled");

    companion object {
        fun fromRaw(raw: String?): NotificationStatus? = entries.firstOrNull { it.raw == raw }
    }
}

// Added in 4.1.0 — delivery and engagement counters of a sent notification (`notifications/{id}.stats`).
data class NotificationStats(
    val recipients: Int = 0,
    val pushTargets: Int = 0,
    val pushSent: Int = 0,
    val pushFailed: Int = 0,
    val opened: Int = 0,
    val actionTapped: Int = 0,
) {
    companion object {
        val ZERO = NotificationStats()
    }
}

// Added in 4.1.0 — the result of `sendNotification`.
data class NotificationSendResult(
    val notificationId: String,
    val status: NotificationStatus,
    /** When it will go out, if scheduled (by the admin or deferred by quiet hours). */
    val scheduledAt: Instant?,
    val recipients: Int,
)

// Added in 4.1.0 — a row of the admin's notification history (`notifications/{id}`).
data class SentNotification(
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val audience: NotificationAudience,
    /** Human-readable audience written by the server ("Sakarya Üniversitesi › Mühendislik Fakültesi · Kadın"). */
    val audienceLabel: String,
    val status: NotificationStatus,
    val isTest: Boolean,
    /** Sent automatically (coins credited, new exam, …) rather than by an admin. */
    val isAutomatic: Boolean,
    val deferredByQuietHours: Boolean,
    val createdAt: Instant?,
    val scheduledAt: Instant?,
    val sentAt: Instant?,
    val stats: NotificationStats,
)
