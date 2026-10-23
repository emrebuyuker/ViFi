package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.AnnouncementsReader
import com.salticusteam.vifi.core.model.Announcement
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** Announcements held in memory (mock flavor, previews, tests); [failure] makes the live read throw. */
class FakeAnnouncementsReader(
    initial: List<Announcement> = SAMPLE,
    private val failure: Throwable? = null,
) : AnnouncementsReader {

    private val items = MutableStateFlow(initial)

    fun set(announcements: List<Announcement>) {
        items.value = announcements
    }

    override fun announcementsChanges(): Flow<List<Announcement>> =
        if (failure != null) flow { throw failure } else items

    companion object {
        val SAMPLE = listOf(
            Announcement("a2", "Yeni sınavlar eklendi", "Bu hafta 40 yeni sınav arşive eklendi.", Instant.parse("2026-10-20T09:00:00Z")),
            Announcement("a1", "ViFi'ye hoş geldin", "Geçmiş sınavlara tek yerden ulaş.", Instant.parse("2026-10-01T09:00:00Z")),
        )
    }
}
