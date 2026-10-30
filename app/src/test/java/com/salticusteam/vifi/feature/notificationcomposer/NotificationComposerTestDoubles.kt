package com.salticusteam.vifi.feature.notificationcomposer

import com.salticusteam.vifi.core.data.notifications.admin.EncodedNotificationImage
import com.salticusteam.vifi.core.data.notifications.admin.NotificationImageEncoder
import com.salticusteam.vifi.core.data.repository.NotificationAdminService
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.AudienceCounts
import com.salticusteam.vifi.core.model.AudienceFacets
import com.salticusteam.vifi.core.model.AudienceNode
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSummary
import com.salticusteam.vifi.core.model.NotificationAudience
import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationImage
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationLocation
import com.salticusteam.vifi.core.model.NotificationPreview
import com.salticusteam.vifi.core.model.NotificationSendResult
import com.salticusteam.vifi.core.model.NotificationStatus
import com.salticusteam.vifi.core.model.SentNotification
import com.salticusteam.vifi.testing.MutableClock
import kotlinx.coroutines.CompletableDeferred

/**
 * The admin service with scripted answers (iOS `StubNotificationAdminService`): every call is recorded, a set failure
 * is thrown by its call, and an open [gate] holds every call until it completes.
 */
class StubNotificationAdminService : NotificationAdminService {

    var facetsResult: Result<AudienceFacets> = Result.success(AudienceFacets(total = 0, universities = emptyList()))
    var previewResult: Result<NotificationPreview> = Result.success(NotificationPreview(recipients = 0, pushTargets = 0))

    /** `null` answers each send with a `sent` result for that draft. */
    var sendResult: Result<NotificationSendResult>? = null

    /** When set, every image upload fails with it. */
    var uploadError: Throwable? = null
    var cancelError: Throwable? = null
    var historyResult: Result<List<SentNotification>> = Result.success(emptyList())

    /** When set, every call waits for it before it is answered. */
    var gate: CompletableDeferred<Unit>? = null

    val previewRequests = mutableListOf<Pair<NotificationKind, NotificationAudience>>()
    val sentDrafts = mutableListOf<NotificationDraft>()
    val canceledIds = mutableListOf<String>()

    /** Every upload attempt, in order, by notification id. */
    val uploadRequests = mutableListOf<String>()

    /** What is "in Storage": the first upload per path wins (overwriting is denied, like the rules). */
    val storedImages = mutableMapOf<String, ByteArray>()

    var audienceCalls = 0
        private set
    var historyCalls = 0
        private set

    override suspend fun audience(): AudienceFacets {
        audienceCalls++
        gate?.await()
        return facetsResult.getOrThrow()
    }

    override suspend fun preview(kind: NotificationKind, audience: NotificationAudience): NotificationPreview {
        previewRequests += kind to audience
        gate?.await()
        return previewResult.getOrThrow()
    }

    override suspend fun uploadImage(jpeg: ByteArray, notificationId: String): String {
        uploadRequests += notificationId
        gate?.await()
        uploadError?.let { throw it }
        val path = NotificationImage.storagePath(notificationId)
        storedImages.getOrPut(path) { jpeg }
        return path
    }

    override suspend fun send(draft: NotificationDraft): NotificationSendResult {
        sentDrafts += draft
        gate?.await()
        sendResult?.let { return it.getOrThrow() }
        return NotificationSendResult(draft.id, NotificationStatus.SENT, scheduledAt = null, recipients = 0)
    }

    override suspend fun cancel(notificationId: String) {
        canceledIds += notificationId
        gate?.await()
        cancelError?.let { throw it }
    }

    override suspend fun history(): List<SentNotification> {
        historyCalls++
        gate?.await()
        return historyResult.getOrThrow()
    }
}

/** Encodes every picked URI to its scripted result (a JPEG by default); an open [gate] for a URI holds it. */
class FakeNotificationImageEncoder : NotificationImageEncoder {

    val results = mutableMapOf<String, EncodedNotificationImage>()
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
    val requests = mutableListOf<String>()

    override suspend fun encode(uri: String): EncodedNotificationImage {
        requests += uri
        gates[uri]?.await()
        return results[uri] ?: EncodedNotificationImage.Jpeg(uri.toByteArray())
    }
}

/** Shared fixtures for the composer's view model suites (iOS `NotificationComposerFixture`). */
class NotificationComposerFixture {

    val clock = MutableClock()
    val analytics = FakeAnalyticsTracker()
    val encoder = FakeNotificationImageEncoder()

    /** A view model over [service]; its facets load once the test dispatcher runs. */
    fun makeViewModel(service: StubNotificationAdminService = StubNotificationAdminService()): NotificationComposerViewModel {
        service.facetsResult = Result.success(FACETS)
        return NotificationComposerViewModel(service, encoder, analytics, clock)
    }

    /** A form that is ready to send to everyone. */
    fun readyViewModel(service: StubNotificationAdminService = StubNotificationAdminService()): NotificationComposerViewModel =
        makeViewModel(service).apply {
            onTitleChange("Başlık")
            onBodyChange("Mesaj")
        }

    companion object {
        val FACETS = AudienceFacets(
            total = 20,
            universities = listOf(
                node("zonguldak", "Zonguldak Üniversitesi", 5, 2, 3),
                node(
                    "sakarya", "Sakarya Üniversitesi", 12, 7, 5,
                    node(
                        "muhendislik", "Mühendislik Fakültesi", 9, 5, 4,
                        node("makine", "Makine Mühendisliği", 6, 4, 2),
                        node("bilgisayar", "Bilgisayar Mühendisliği", 3, 1, 2),
                    ),
                    node("iibf", "İktisadi ve İdari Bilimler Fakültesi", 3, 2, 1),
                ),
                node("ankara", "Ankara Üniversitesi", 3, 1, 2),
            ),
        )

        val CAFE = NotificationLocation(
            latitude = 40.9903,
            longitude = 29.029,
            name = "ViFi Öğrenci Kafesi",
            address = "Caferağa, Kadıköy",
            placeId = "place-1",
        )

        val EXAM = ArchiveItem(
            path = ArchivePath(
                listOf("uni", "uni__fac", "uni__fac__dep", "uni__fac__dep__course", "exam-42"),
                listOf("Üniversite", "Fakülte", "Bölüm", "Ders", "2024 VİZE"),
            ),
            level = ArchiveLevel.EXAM,
            childCount = null,
            exam = ExamSummary(ExamKind.PDF, fileCount = 1),
        )

        private fun node(id: String, name: String, total: Int, female: Int, male: Int, vararg children: AudienceNode) =
            AudienceNode(id, name, AudienceCounts(total, female, male), children.toList())
    }
}
