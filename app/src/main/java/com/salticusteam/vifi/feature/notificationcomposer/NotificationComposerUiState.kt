package com.salticusteam.vifi.feature.notificationcomposer

import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.AudienceFacets
import com.salticusteam.vifi.core.model.AudienceNode
import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.NotificationAudience
import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationLocation
import com.salticusteam.vifi.core.model.NotificationPreview
import com.salticusteam.vifi.core.model.NotificationSegment
import com.salticusteam.vifi.core.model.codePointLength
import java.text.Collator
import java.time.Instant
import java.util.Locale

/** Who the notification is for, as the form offers it; the server-only audiences are not selectable. */
enum class AudienceMode { EVERYONE, SEGMENT }

/** The button the notification's card offers ("Buton"), in the picker's order. */
enum class ActionChoice { NONE, PAYWALL, ADD_EXAM, URL, EXAM, LOCATION }

/** What the confirmation dialog shows: the draft exactly as it will be sent and the server's exact reach for it. */
data class NotificationSendConfirmation(val draft: NotificationDraft, val preview: NotificationPreview)

/** The picked campaign image, encoded and ready to upload. Identity equality: every pick is a new image. */
class ComposerImage(val jpeg: ByteArray)

/** Why a picked photo was not taken; the previous image stays. */
enum class ComposerImageError {
    /** The photo could not be read ("Görsel yüklenemedi. Başka bir görsel seç."). */
    UNREADABLE,

    /** Not an image, or too large even at the lowest quality ("Bu görsel kullanılamıyor. Başka bir görsel seç."). */
    UNUSABLE,
}

/** A failure the feedback section shows. */
sealed interface ComposerError {
    /** A domain error's Turkish `userMessage` (the server's own text for a refused draft). */
    data class Message(val text: String) : ComposerError

    /** The server recorded the notification as failed or canceled ("Bildirim gönderilemedi. …"). */
    data object SendFailed : ComposerError
}

/** What went through, for the feedback section. */
sealed interface ComposerSuccess {
    /** Sent now; the server's recipient count. */
    data class Sent(val recipients: Int) : ComposerSuccess

    /** Scheduled for [at] (by the admin, or held over quiet hours); no count, the audience is resolved at delivery. */
    data class Scheduled(val at: Instant?) : ComposerSuccess

    /** A test went to the admin's own devices. */
    data object TestSent : ComposerSuccess
}

/**
 * The admin "Bildirim Gönder" form (iOS `NotificationComposerViewModel`'s state): the draft's fields, the audience
 * pickers' data, the image, and the send flow. The derived values (the draft, its issue, whether it can be sent, the
 * reach estimate) are computed from these fields; scheduling checks are relative to [now], refreshed on every change.
 */
data class NotificationComposerUiState(
    /** The id of the draft being composed: stable across retries of one send, new once a send has gone through. */
    val draftId: String = NotificationDraft.newId(),
    /** The admin's own choice; while Konum is the action the form shows and sends [effectiveKind] instead. */
    val kind: NotificationKind = NotificationKind.GENERAL,
    val title: String = "",
    val body: String = "",
    val audienceMode: AudienceMode = AudienceMode.EVERYONE,
    val universityId: String? = null,
    val facultyId: String? = null,
    val departmentId: String? = null,
    val gender: Gender? = null,
    val actionChoice: ActionChoice = ActionChoice.NONE,
    /** Kept while another action is chosen, like [location] and [selectedExam]; only the chosen action is sent. */
    val urlText: String = "",
    val location: NotificationLocation? = null,
    /** The exam the `exam` action opens, picked from the archive. */
    val selectedExam: ArchiveItem? = null,
    val image: ComposerImage? = null,
    val isPreparingImage: Boolean = false,
    val imageError: ComposerImageError? = null,
    /** `null` sends right away. */
    val scheduledAt: Instant? = null,
    val expiresAt: Instant? = null,
    /** The audience pickers' data; `null` while loading or after a failure ([facetsError]). */
    val facets: AudienceFacets? = null,
    val facetsError: String? = null,
    val isWorking: Boolean = false,
    val error: ComposerError? = null,
    val success: ComposerSuccess? = null,
    /** The preview awaiting the admin's yes; kept until the send resolves or is declined. */
    val confirmation: NotificationSendConfirmation? = null,
    /** Whether the confirmation dialog is up; separate from [confirmation], which the send itself reads. */
    val isConfirming: Boolean = false,
    /** When the derived values were last evaluated. */
    val now: Instant = Instant.EPOCH,
) {
    val isFacetsLoading: Boolean get() = facets == null && facetsError == null

    /** A location notification is always a campaign: "Tür" shows Kampanya and cannot be changed meanwhile. */
    val isKindLocked: Boolean get() = actionChoice == ActionChoice.LOCATION

    /** The kind the form shows and sends: Kampanya while locked, otherwise the admin's own choice. */
    val effectiveKind: NotificationKind get() = if (isKindLocked) NotificationKind.PROMO else kind

    /** Universities that have registered users, by name. */
    val universities: List<AudienceNode> get() = sorted(facets?.universities)

    val faculties: List<AudienceNode>
        get() {
            val universityId = universityId ?: return emptyList()
            return sorted(facets?.faculties(universityId))
        }

    val departments: List<AudienceNode>
        get() {
            val universityId = universityId ?: return emptyList()
            val facultyId = facultyId ?: return emptyList()
            return sorted(facets?.departments(universityId, facultyId))
        }

    /**
     * The users the current audience reaches according to the registered profiles, or `null` while unknown. Campaigns
     * are narrowed further to users who opted in; only the preview knows that number.
     */
    val estimatedRecipients: Int?
        get() {
            val facets = facets ?: return null
            return when (audienceMode) {
                AudienceMode.EVERYONE -> facets.total
                AudienceMode.SEGMENT -> segment?.let(facets::estimatedRecipients)
            }
        }

    /** The draft as the form currently stands (an action that cannot be built is left out; [issue] reports it). */
    val draft: NotificationDraft
        get() = NotificationDraft(
            id = draftId,
            kind = effectiveKind,
            title = title,
            body = body,
            action = action,
            audience = audience,
            scheduledAt = scheduledAt,
            expiresAt = expiresAt,
        )

    /** The first problem that blocks sending, or `null` when the form is ready. */
    val issue: NotificationDraft.Issue? get() = issue(draft)

    /** The issue worth showing now: an empty title or message is not flagged before the admin has typed anything. */
    val visibleIssue: NotificationDraft.Issue?
        get() = when (val issue = issue) {
            NotificationDraft.Issue.EMPTY_TITLE -> issue.takeUnless { title.isEmpty() }
            NotificationDraft.Issue.EMPTY_BODY -> issue.takeUnless { body.isEmpty() }
            else -> issue
        }

    // A picked image still being encoded would otherwise be left out of the send.
    val canSend: Boolean get() = !isWorking && !isPreparingImage && issue == null

    val canSendTest: Boolean get() = !isWorking && !isPreparingImage && issue(testDraft(draftId)) == null

    /** Counted like the server: the trimmed text, in code points. */
    val titleLength: Int get() = title.trim().codePointLength()

    val bodyLength: Int get() = body.trim().codePointLength()

    /** Whether the feedback section has anything to show. */
    val hasFeedback: Boolean get() = visibleIssue != null || error != null || success != null

    /**
     * The test copy: addressed to the admin only, so it goes out at once with no validity, and an unfinished segment
     * (which a test does not need) falls back to everyone rather than blocking the test.
     */
    fun testDraft(id: String): NotificationDraft {
        val test = draft.copy(id = id, isTest = true, scheduledAt = null, expiresAt = null)
        return if (test.validationIssue(now) == NotificationDraft.Issue.INVALID_AUDIENCE) {
            test.copy(audience = NotificationAudience.All)
        } else {
            test
        }
    }

    /** [draft]'s first problem, including a chosen action that produced none (never sent as "no action"). */
    fun issue(draft: NotificationDraft): NotificationDraft.Issue? {
        draft.validationIssue(now)?.let { return it }
        // A link left empty, no exam picked, or no place picked yet left the draft without an action; do not send it
        // as if none was chosen.
        if (draft.action != null) return null
        return when (actionChoice) {
            ActionChoice.URL -> NotificationDraft.Issue.INVALID_URL
            ActionChoice.EXAM -> NotificationDraft.Issue.EMPTY_EXAM
            ActionChoice.LOCATION -> NotificationDraft.Issue.INVALID_LOCATION
            ActionChoice.NONE, ActionChoice.PAYWALL, ActionChoice.ADD_EXAM -> null
        }
    }

    // Helpers
    private val segment: NotificationSegment?
        get() {
            val universityId = universityId?.takeIf { it.isNotEmpty() } ?: return null
            return NotificationSegment(universityId, facultyId, departmentId, gender)
        }

    private val audience: NotificationAudience
        get() = when (audienceMode) {
            AudienceMode.EVERYONE -> NotificationAudience.All
            // Without a university the draft reports an invalid audience instead of silently going to everyone.
            AudienceMode.SEGMENT -> NotificationAudience.Segment(segment ?: NotificationSegment("", gender = gender))
        }

    private val action: NotificationDraft.Action?
        get() = when (actionChoice) {
            ActionChoice.NONE -> null
            ActionChoice.PAYWALL -> NotificationDraft.Action.Paywall
            ActionChoice.ADD_EXAM -> NotificationDraft.Action.AddExam
            ActionChoice.URL -> urlText.trim().takeIf { it.isNotEmpty() }?.let(NotificationDraft.Action::Url)
            ActionChoice.LOCATION -> location?.let(NotificationDraft.Action::Location)
            // The exam document's id is the path's last component; only that is sent.
            ActionChoice.EXAM -> selectedExam?.path?.components?.lastOrNull()?.let(NotificationDraft.Action::Exam)
        }

    private companion object {
        /** By name, Turkish collation (so İ, Ş, Ç sort where a Turkish reader expects them). */
        fun sorted(nodes: List<AudienceNode>?): List<AudienceNode> {
            if (nodes.isNullOrEmpty()) return emptyList()
            val collator = Collator.getInstance(Locale.forLanguageTag("tr-TR"))
            return nodes.sortedWith { first, second -> collator.compare(first.name, second.name) }
        }
    }
}
