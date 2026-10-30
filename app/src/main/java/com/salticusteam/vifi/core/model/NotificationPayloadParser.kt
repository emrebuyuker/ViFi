package com.salticusteam.vifi.core.model

import java.time.Instant
import java.util.Date

// Added in 4.1.0 — pure parsers for the notification contract: Firestore documents, callable results and push data.
/**
 * Free of Firebase types so they are unit-tested directly (iOS `NotificationPayloadParser` parity). Dates arrive as
 * an [Instant], a [Date] or epoch milliseconds; the live Firestore readers convert their `Timestamp`s first. Unknown
 * values are tolerated where the app can still show something useful (an unknown kind reads as general, an unknown
 * action is dropped), so a newer server never blanks an older app's inbox.
 */
object NotificationPayloadParser {

    private val NOTIFICATION_ID = Regex("^[A-Za-z0-9_-]{1,128}$")

    // --- Inbox ---

    /** An inbox entry (`announcements/{id}` or `users/{uid}/inbox/{id}`); `null` without a title or a safe id. */
    fun inboxItem(id: String, source: InboxSource, data: Map<String, Any?>): InboxItem? {
        if (!isValidNotificationId(id)) return null
        val title = string(data["title"]) ?: return null
        return InboxItem(
            id = id,
            source = source,
            kind = kind(data["kind"]),
            title = title,
            body = string(data["body"]).orEmpty(),
            imageStoragePath = imageStoragePath(data["image"]),
            action = action(data["action"]),
            createdAt = date(data["createdAt"]),
            expiresAt = date(data["expiresAt"]),
        )
    }

    /**
     * The image's Storage path (`image: { storagePath }`); `null` when absent or not a notification image path, so a
     * stray value can never make the app download something else with the user's credentials.
     */
    fun imageStoragePath(value: Any?): String? {
        val path = string((value as? Map<*, *>)?.get("storagePath")) ?: return null
        return path.takeIf(NotificationImage::isValidStoragePath)
    }

    /** A notification's action; `null` when absent, unknown or malformed (the entry is kept without a button). */
    fun action(value: Any?): NotificationAction? {
        val data = value as? Map<*, *> ?: return null
        return when (string(data["type"])) {
            "location" -> location(data)?.let(NotificationAction::Location)
            "exam" -> examTarget(data)?.let(NotificationAction::Exam)
            "paywall" -> NotificationAction.Paywall
            "addExam" -> NotificationAction.AddExam
            "url" -> string(data["url"])?.takeIf(::isHttpsLink)?.let(NotificationAction::Url)
            else -> null
        }
    }

    /** A valid location (in range, with a name), or `null`. */
    fun location(data: Map<*, *>): NotificationLocation? {
        val latitude = double(data["latitude"]) ?: return null
        val longitude = double(data["longitude"]) ?: return null
        val name = string(data["name"]) ?: return null
        val location = NotificationLocation(
            latitude = latitude,
            longitude = longitude,
            name = name,
            address = string(data["address"]),
            placeId = string(data["placeId"]),
        )
        return location.takeIf { it.isValid }
    }

    // --- Admin ---

    /** The `getNotificationAudience` result. */
    fun facets(value: Any?): AudienceFacets? {
        val data = value as? Map<*, *> ?: return null
        val universities = data["universities"] as? List<*> ?: return null
        return AudienceFacets(total = int(data["total"]), universities = universities.mapNotNull(::audienceNode))
    }

    /** The `previewNotification` result. */
    fun preview(value: Any?): NotificationPreview? {
        val data = value as? Map<*, *> ?: return null
        if (data["recipients"] == null) return null
        return NotificationPreview(recipients = int(data["recipients"]), pushTargets = int(data["pushTargets"]))
    }

    /** The `sendNotification` result; `null` without a safe id or a known status. */
    fun sendResult(value: Any?): NotificationSendResult? {
        val data = value as? Map<*, *> ?: return null
        val id = string(data["notificationId"])?.takeIf(::isValidNotificationId) ?: return null
        val status = NotificationStatus.fromRaw(string(data["status"])) ?: return null
        return NotificationSendResult(
            notificationId = id,
            status = status,
            scheduledAt = date(data["scheduledAt"]),
            recipients = int(data["recipients"]),
        )
    }

    /** A row of the admin history (`notifications/{id}`); `null` without a title, a known status or an audience. */
    fun sentNotification(id: String, data: Map<String, Any?>): SentNotification? {
        if (!isValidNotificationId(id)) return null
        val title = string(data["title"]) ?: return null
        val status = NotificationStatus.fromRaw(string(data["status"])) ?: return null
        val audience = audience(data["audience"]) ?: return null
        return SentNotification(
            id = id,
            kind = kind(data["kind"]),
            title = title,
            body = string(data["body"]).orEmpty(),
            audience = audience,
            audienceLabel = string(data["audienceLabel"]).orEmpty(),
            status = status,
            isTest = data["isTest"] as? Boolean ?: false,
            isAutomatic = string(data["source"]) == "automatic",
            deferredByQuietHours = data["deferredByQuietHours"] as? Boolean ?: false,
            createdAt = date(data["createdAt"]),
            scheduledAt = date(data["scheduledAt"]),
            sentAt = date(data["sentAt"]),
            stats = stats(data["stats"]),
        )
    }

    fun audience(value: Any?): NotificationAudience? {
        val data = value as? Map<*, *> ?: return null
        return when (string(data["scope"])) {
            "all" -> NotificationAudience.All
            "segment" -> string(data["universityId"])?.let { universityId ->
                NotificationAudience.Segment(
                    NotificationSegment(
                        universityId = universityId,
                        facultyId = string(data["facultyId"]),
                        departmentId = string(data["departmentId"]),
                        gender = Gender.fromRaw(string(data["gender"])),
                    ),
                )
            }
            "user" -> string(data["uid"])?.let(NotificationAudience::User)
            "admins" -> NotificationAudience.Admins
            else -> null
        }
    }

    // --- Push ---

    /**
     * The notification a tapped push belongs to (`data.notificationId`, which also arrives as a launch-intent extra),
     * or `null` for pushes that carry none (a hand-made Console message, a silent auth push) or an unsafe one.
     */
    fun notificationIdFromPush(data: Map<String, Any?>): String? =
        (data["notificationId"] as? String)?.takeIf(::isValidNotificationId)

    /** Ids are client UUIDs or Firestore auto-ids; anything else is refused (it becomes a document id). */
    fun isValidNotificationId(id: String): Boolean = NOTIFICATION_ID.matches(id)

    // --- Values ---

    fun date(value: Any?): Instant? = when (value) {
        is Instant -> value
        is Date -> value.toInstant()
        else -> double(value)?.let { Instant.ofEpochMilli(it.toLong()) }
    }

    private fun kind(value: Any?): NotificationKind = NotificationKind.fromRaw(string(value)) ?: NotificationKind.GENERAL

    private fun examTarget(data: Map<*, *>): NotificationExamTarget? {
        val examId = string(data["examId"]) ?: return null
        val ids = strings(data["path"]) ?: return null
        if (ids.size != ArchiveLevel.entries.size || ids.last() != examId) return null
        val kind = ExamKind.fromRaw(string(data["examKind"])) ?: return null
        return NotificationExamTarget(examId = examId, path = ArchivePath(ids, strings(data["names"])), kind = kind)
    }

    private fun audienceNode(value: Any?): AudienceNode? {
        val data = value as? Map<*, *> ?: return null
        val id = string(data["id"]) ?: return null
        val children = (data["faculties"] as? List<*>) ?: (data["departments"] as? List<*>) ?: emptyList<Any?>()
        return AudienceNode(
            id = id,
            name = string(data["name"]) ?: id,
            counts = AudienceCounts(total = int(data["count"]), female = int(data["female"]), male = int(data["male"])),
            children = children.mapNotNull(::audienceNode),
        )
    }

    private fun stats(value: Any?): NotificationStats {
        val data = value as? Map<*, *> ?: return NotificationStats.ZERO
        return NotificationStats(
            recipients = int(data["recipients"]),
            pushTargets = int(data["pushTargets"]),
            pushSent = int(data["pushSent"]),
            pushFailed = int(data["pushFailed"]),
            opened = int(data["opened"]),
            actionTapped = int(data["actionTapped"]),
        )
    }

    /** Trimmed text; blank means absent. */
    private fun string(value: Any?): String? = (value as? String)?.trim()?.takeIf { it.isNotEmpty() }

    /** A list made only of strings, or `null`. */
    private fun strings(value: Any?): List<String>? {
        val list = value as? List<*> ?: return null
        return list.map { it as? String ?: return null }
    }

    /** A finite number; a Boolean is never a coordinate, a count or a date. */
    private fun double(value: Any?): Double? = (value as? Number)?.toDouble()?.takeIf { it.isFinite() }

    private fun int(value: Any?): Int {
        val number = double(value) ?: return 0
        return if (number >= 0 && number < Int.MAX_VALUE) number.toInt() else 0
    }

    private fun isHttpsLink(text: String): Boolean = NotificationDraft.isHttpsUrl(text)
}
