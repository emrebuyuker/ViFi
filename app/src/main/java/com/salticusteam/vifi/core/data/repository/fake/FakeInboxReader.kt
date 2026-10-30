package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.InboxReader
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.InboxItem
import com.salticusteam.vifi.core.model.InboxSource
import com.salticusteam.vifi.core.model.NotificationAction
import com.salticusteam.vifi.core.model.NotificationExamTarget
import com.salticusteam.vifi.core.model.NotificationImage
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationLocation
import com.salticusteam.vifi.core.util.StorageUrl
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The inbox held in memory (mock flavor, previews, tests; iOS `StubInboxReader`). The samples use the iOS ids so UI
 * tests and `vifi_open_notification <id>` work the same on both platforms; their exam actions point at
 * [FakeArchiveRepository]'s sample lesson. A failure set before a flow is collected makes that flow throw.
 */
class FakeInboxReader(
    broadcast: List<InboxItem> = SAMPLE_BROADCAST,
    personal: List<InboxItem> = SAMPLE_PERSONAL,
    readAt: Instant? = SAMPLE_READ_AT,
    private val now: () -> Instant = Instant::now,
) : InboxReader {

    var broadcastFailure: Throwable? = null
    var personalFailure: Throwable? = null
    var markerFailure: Throwable? = null
    var markReadFailure: Throwable? = null

    var markReadCount = 0
        private set

    /** The uids the personal inbox and the marker were observed for, in order. */
    val observedUids = mutableListOf<String>()

    private val broadcastItems = MutableStateFlow(broadcast)
    private val personalItems = MutableStateFlow(personal)
    private val marker = MutableStateFlow(readAt)

    fun setBroadcast(items: List<InboxItem>) {
        broadcastItems.value = items
    }

    fun setPersonal(items: List<InboxItem>) {
        personalItems.value = items
    }

    /** Simulates the marker changing elsewhere (another device opened the inbox). */
    fun setReadAt(readAt: Instant?) {
        marker.value = readAt
    }

    override fun broadcastChanges(): Flow<List<InboxItem>> = flow {
        broadcastFailure?.let { throw it }
        emitAll(broadcastItems)
    }

    override fun personalChanges(uid: String): Flow<List<InboxItem>> = flow {
        observedUids += uid
        personalFailure?.let { throw it }
        emitAll(personalItems)
    }

    override fun readMarkerChanges(uid: String): Flow<Instant?> = flow {
        markerFailure?.let { throw it }
        emitAll(marker)
    }

    override suspend fun markRead(uid: String) {
        markReadCount++
        markReadFailure?.let { throw it }
        marker.value = now()
    }

    /** A Storage-shaped URL; the mock flavor's file client answers it with a bundled sample image, offline. */
    override fun imageUrl(storagePath: String): String? =
        storagePath.takeIf(NotificationImage::isValidStoragePath)?.let { StorageUrl.canonical(it, MOCK_BUCKET) }

    companion object {
        const val MOCK_BUCKET = "vifi-mock.appspot.com"

        /** Before every sample, so they all start unread. */
        val SAMPLE_READ_AT: Instant = Instant.ofEpochSecond(1_757_000_000)

        private val LESSON = ArchivePath(
            listOf("itu", "muh", "blg", "blg101"),
            listOf("İstanbul Teknik Üniversitesi", "Mühendislik", "Bilgisayar Mühendisliği", "BLG 101"),
        )

        val SAMPLE_BROADCAST = listOf(
            InboxItem(
                id = "a1",
                source = InboxSource.BROADCAST,
                kind = NotificationKind.GENERAL,
                title = "Yeni sınavlar eklendi",
                body = "Bilgisayar Mühendisliği'ne 2024 vize ve finalleri eklendi.",
                createdAt = Instant.ofEpochSecond(1_763_000_000),
            ),
            InboxItem(
                id = "a2",
                source = InboxSource.BROADCAST,
                kind = NotificationKind.GENERAL,
                title = "ViFi'ye hoş geldin",
                body = "Geçmiş sınavlara tek yerden ulaş, sınav ekleyerek coin kazan.",
                createdAt = Instant.ofEpochSecond(1_759_000_000),
            ),
            InboxItem(
                id = "a-expired",
                source = InboxSource.BROADCAST,
                kind = NotificationKind.GENERAL,
                title = "Süresi dolmuş duyuru",
                body = "Bu bildirim listede görünmemeli.",
                createdAt = Instant.ofEpochSecond(1_760_000_000),
                expiresAt = Instant.ofEpochSecond(1_760_500_000),
            ),
        )

        val SAMPLE_PERSONAL = listOf(
            InboxItem(
                id = "p-exam",
                source = InboxSource.PERSONAL,
                kind = NotificationKind.SYSTEM,
                title = "Sınavın onaylandı",
                body = "Gönderdiğin sınav arşive eklendi. Göz atmak ister misin?",
                // The lesson's oldest exam: free to open.
                action = examAction("2020-final", "2020 Final", ExamKind.PDF),
                createdAt = Instant.ofEpochSecond(1_762_000_000),
            ),
            InboxItem(
                id = "p-paywall",
                source = InboxSource.PERSONAL,
                kind = NotificationKind.PROMO,
                title = "Premium'da bahar fırsatı",
                body = "Tüm arşivin kilidini aç, kampanyalı fiyatı kaçırma.",
                action = NotificationAction.Paywall,
                createdAt = Instant.ofEpochSecond(1_761_000_000),
            ),
            InboxItem(
                id = "p-exam-locked",
                source = InboxSource.PERSONAL,
                kind = NotificationKind.GENERAL,
                title = "Yeni final soruları eklendi",
                body = "BLG 101 2022 finali arşivde.",
                imageStoragePath = NotificationImage.storagePath("p-exam-locked"),
                // One of the lesson's two newest exams: coin-locked.
                action = examAction("2022-final", "2022 Final", ExamKind.IMAGES),
                createdAt = Instant.ofEpochSecond(1_760_800_000),
            ),
            InboxItem(
                id = "p-location",
                source = InboxSource.PERSONAL,
                kind = NotificationKind.PROMO,
                title = "Kampüse yakın kahve fırsatı",
                body = "ViFi'lilere özel %20 indirim, bu hafta sonuna kadar.",
                action = NotificationAction.Location(
                    NotificationLocation(
                        latitude = 40.9903,
                        longitude = 29.0290,
                        name = "ViFi Öğrenci Kafesi",
                        address = "Caferağa, Kadıköy, İstanbul",
                    ),
                ),
                createdAt = Instant.ofEpochSecond(1_758_000_000),
            ),
        )

        private fun examAction(examId: String, name: String, kind: ExamKind): NotificationAction {
            val path = LESSON.appending(id = examId, displayName = name)
            return NotificationAction.Exam(NotificationExamTarget(examId = examId, path = path, kind = kind))
        }
    }
}
