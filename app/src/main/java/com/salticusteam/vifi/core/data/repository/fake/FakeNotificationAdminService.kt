package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.NotificationAdminService
import com.salticusteam.vifi.core.model.AudienceCounts
import com.salticusteam.vifi.core.model.AudienceFacets
import com.salticusteam.vifi.core.model.AudienceNode
import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.NotificationAdminError
import com.salticusteam.vifi.core.model.NotificationAudience
import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationImage
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationPreview
import com.salticusteam.vifi.core.model.NotificationSegment
import com.salticusteam.vifi.core.model.NotificationSendResult
import com.salticusteam.vifi.core.model.NotificationStats
import com.salticusteam.vifi.core.model.NotificationStatus
import com.salticusteam.vifi.core.model.SentNotification
import java.time.Instant

/**
 * Admin notification sending in memory (mock flavor, previews, tests; iOS `MockNotificationAdminService`): sample
 * audience data, sends succeed and land at the top of the history, a send is idempotent per draft id, and an image
 * uploaded for an id is kept like the Storage rules keep it. The audience labels follow the server's wording. A
 * failure set for a call is thrown by that call instead.
 */
class FakeNotificationAdminService(
    history: List<SentNotification> = SAMPLE_HISTORY,
    private val facets: AudienceFacets = SAMPLE_FACETS,
    private val now: () -> Instant = Instant::now,
) : NotificationAdminService {

    var audienceFailure: Throwable? = null
    var previewFailure: Throwable? = null
    var uploadFailure: Throwable? = null
    var sendFailure: Throwable? = null
    var cancelFailure: Throwable? = null
    var historyFailure: Throwable? = null

    /** Every draft handed to [send], in order (a repeated id included). */
    val sentDrafts = mutableListOf<NotificationDraft>()

    /** Every [preview] request, in order. */
    val previewRequests = mutableListOf<Pair<NotificationKind, NotificationAudience>>()

    val canceledIds = mutableListOf<String>()

    /** Uploaded images by Storage path; the first upload for a path wins. */
    val uploadedImages = mutableMapOf<String, ByteArray>()

    var uploadCount = 0
        private set

    private val rows = history.toMutableList()

    override suspend fun audience(): AudienceFacets {
        audienceFailure?.let { throw it }
        return facets
    }

    override suspend fun preview(kind: NotificationKind, audience: NotificationAudience): NotificationPreview {
        previewRequests += kind to audience
        previewFailure?.let { throw it }
        val recipients = reach(kind, audience)
        return NotificationPreview(recipients = recipients, pushTargets = recipients)
    }

    override suspend fun uploadImage(jpeg: ByteArray, notificationId: String): String {
        uploadCount++
        uploadFailure?.let { throw it }
        val path = NotificationImage.storagePath(notificationId)
        uploadedImages.getOrPut(path) { jpeg }
        return path
    }

    override suspend fun send(draft: NotificationDraft): NotificationSendResult {
        sentDrafts += draft
        sendFailure?.let { throw it }
        rows.firstOrNull { it.id == draft.id }?.let { existing ->
            return NotificationSendResult(existing.id, existing.status, existing.scheduledAt, existing.stats.recipients)
        }
        val recipients = if (draft.isTest) 1 else reach(draft.effectiveKind, draft.audience)
        val status = if (draft.scheduledAt == null) NotificationStatus.SENT else NotificationStatus.SCHEDULED
        val sentNow = status == NotificationStatus.SENT
        val createdAt = now()
        rows.add(
            0,
            SentNotification(
                id = draft.id,
                kind = draft.effectiveKind,
                title = draft.trimmedTitle,
                body = draft.trimmedBody,
                audience = draft.audience,
                audienceLabel = audienceLabel(draft.audience),
                status = status,
                isTest = draft.isTest,
                isAutomatic = false,
                deferredByQuietHours = false,
                createdAt = createdAt,
                scheduledAt = draft.scheduledAt,
                sentAt = if (sentNow) createdAt else null,
                stats = NotificationStats(
                    recipients = recipients,
                    pushTargets = recipients,
                    pushSent = if (sentNow) recipients else 0,
                ),
            ),
        )
        // Like the server, a scheduled send reports no recipients yet.
        return NotificationSendResult(draft.id, status, draft.scheduledAt, if (sentNow) recipients else 0)
    }

    override suspend fun cancel(notificationId: String) {
        cancelFailure?.let { throw it }
        val index = rows.indexOfFirst { it.id == notificationId }
        if (index < 0 || rows[index].status != NotificationStatus.SCHEDULED) throw NotificationAdminError.NotCancelable
        rows[index] = rows[index].copy(status = NotificationStatus.CANCELED, sentAt = null)
        canceledIds += notificationId
    }

    override suspend fun history(): List<SentNotification> {
        historyFailure?.let { throw it }
        return rows.toList()
    }

    private fun reach(kind: NotificationKind, audience: NotificationAudience): Int {
        val reach = when (audience) {
            NotificationAudience.All -> facets.total
            is NotificationAudience.Segment -> facets.estimatedRecipients(audience.segment)
            is NotificationAudience.User, NotificationAudience.Admins -> 1
        }
        // Campaigns reach only users who opted in; about half in the sample data.
        return if (kind == NotificationKind.PROMO) reach / 2 else reach
    }

    /** The server's `audienceLabel` wording ("Üniv › Fakülte › Bölüm · Kadın"), named from [facets]. */
    private fun audienceLabel(audience: NotificationAudience): String = when (audience) {
        NotificationAudience.All -> "Tüm kullanıcılar"
        is NotificationAudience.User -> "Kişisel"
        NotificationAudience.Admins -> "Adminler"
        is NotificationAudience.Segment -> {
            val segment = audience.segment
            val university = facets.universities.firstOrNull { it.id == segment.universityId }
            val faculty = university?.children?.firstOrNull { it.id == segment.facultyId }
            val department = faculty?.children?.firstOrNull { it.id == segment.departmentId }
            val parts = listOfNotNull(
                university?.name ?: segment.universityId,
                segment.facultyId?.let { faculty?.name ?: it },
                segment.departmentId?.let { department?.name ?: it },
            )
            val gender = when (segment.gender) {
                Gender.FEMALE -> "Kadın"
                Gender.MALE -> "Erkek"
                null -> null
            }
            parts.joinToString(" › ") + (gender?.let { " · $it" } ?: "")
        }
    }

    companion object {
        val SAMPLE_FACETS = AudienceFacets(
            total = 64,
            universities = listOf(
                node(
                    "bogazici-universitesi", "Boğaziçi Üniversitesi", 40, 22, 18,
                    node(
                        "muhendislik-fakultesi", "Mühendislik Fakültesi", 28, 14, 14,
                        node("bilgisayar-muhendisligi", "Bilgisayar Mühendisliği", 17, 8, 9),
                        node("endustri-muhendisligi", "Endüstri Mühendisliği", 11, 6, 5),
                    ),
                    node(
                        "iktisadi-ve-idari-bilimler-fakultesi", "İktisadi ve İdari Bilimler Fakültesi", 12, 8, 4,
                        node("isletme", "İşletme", 12, 8, 4),
                    ),
                ),
                node(
                    "sakarya-universitesi", "Sakarya Üniversitesi", 24, 9, 15,
                    node(
                        "muhendislik-fakultesi", "Mühendislik Fakültesi", 24, 9, 15,
                        node("makine-muhendisligi", "Makine Mühendisliği", 24, 9, 15),
                    ),
                ),
            ),
        )

        val SAMPLE_HISTORY = listOf(
            SentNotification(
                id = "sample-promo",
                kind = NotificationKind.PROMO,
                title = "Kampüs kafede %20 indirim",
                body = "Öğrenci kartını göster, tüm içeceklerde %20 indirim kazan.",
                audience = NotificationAudience.Segment(NotificationSegment(universityId = "bogazici-universitesi")),
                audienceLabel = "Boğaziçi Üniversitesi",
                status = NotificationStatus.SENT,
                isTest = false,
                isAutomatic = false,
                deferredByQuietHours = true,
                createdAt = Instant.ofEpochSecond(1_761_700_000),
                scheduledAt = Instant.ofEpochSecond(1_761_717_600),
                sentAt = Instant.ofEpochSecond(1_761_717_600),
                stats = NotificationStats(recipients = 18, pushTargets = 21, pushSent = 20, pushFailed = 1, opened = 9, actionTapped = 4),
            ),
            SentNotification(
                id = "sample-general",
                kind = NotificationKind.GENERAL,
                title = "Yeni sınavlar eklendi",
                body = "Bilgisayar Mühendisliği'ne 2024 vize ve finalleri eklendi.",
                audience = NotificationAudience.All,
                audienceLabel = "Tüm kullanıcılar",
                status = NotificationStatus.SENT,
                isTest = false,
                isAutomatic = false,
                deferredByQuietHours = false,
                createdAt = Instant.ofEpochSecond(1_761_000_000),
                scheduledAt = null,
                sentAt = Instant.ofEpochSecond(1_761_000_000),
                stats = NotificationStats(recipients = 64, pushTargets = 70, pushSent = 70, opened = 31),
            ),
        )

        private fun node(id: String, name: String, total: Int, female: Int, male: Int, vararg children: AudienceNode) =
            AudienceNode(id, name, AudienceCounts(total, female, male), children.toList())
    }
}
