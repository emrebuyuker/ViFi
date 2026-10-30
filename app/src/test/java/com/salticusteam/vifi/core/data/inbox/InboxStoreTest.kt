package com.salticusteam.vifi.core.data.inbox

import com.salticusteam.vifi.core.data.preferences.fake.FakeLegacyInboxReadState
import com.salticusteam.vifi.core.data.repository.InboxReader
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.model.InboxError
import com.salticusteam.vifi.core.model.InboxItem
import com.salticusteam.vifi.core.model.InboxSource
import com.salticusteam.vifi.core.model.NotificationKind
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `InboxStoreTests` (29), one for one. */
@OptIn(ExperimentalCoroutinesApi::class)
class InboxStoreTest {

    private val t0: Instant = Instant.parse("2026-10-30T09:00:00Z")
    private val reader = ScriptedInboxReader()
    private val legacy = FakeLegacyInboxReadState(hasLegacyIds = true)
    private val clock = MutableClock(t0.plusSeconds(3_600))
    private val sessions = MutableStateFlow<SessionState>(signedIn("u-1"))

    private fun TestScope.sut() = InboxStore(reader, legacy, clock, backgroundScope).apply { start(sessions) }

    private fun item(
        id: String,
        createdAt: Instant? = t0,
        source: InboxSource = InboxSource.BROADCAST,
        expiresAt: Instant? = null,
        title: String = id,
    ) = InboxItem(id, source, NotificationKind.GENERAL, title, "", createdAt = createdAt, expiresAt = expiresAt)

    private fun signedIn(uid: String) = SessionState.SignedIn(FakeAuthService.DEFAULT_USER.copy(id = uid))

    // --- Loading, merge, failures ---

    @Test
    fun `loading until both sources answer`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        assertTrue(sut.state.value.isLoading)

        reader.broadcast(item("a1"))
        assertTrue(sut.state.value.isLoading)
        reader.personal()

        assertFalse(sut.state.value.isLoading)
        assertEquals(listOf("a1"), sut.state.value.items.map { it.id })
    }

    @Test
    fun `merges, sorts newest first with undated last, and lists a shared id once as personal`() =
        runTest(UnconfinedTestDispatcher()) {
            val sut = sut()

            reader.broadcast(item("old", t0.minusSeconds(60)), item("undated", null), item("shared", t0, title = "genel"))
            reader.personal(item("new", t0.plusSeconds(60), InboxSource.PERSONAL), item("shared", t0, InboxSource.PERSONAL, title = "kişisel"))

            assertEquals(listOf("new", "shared", "old", "undated"), sut.state.value.items.map { it.id })
            assertEquals("kişisel", sut.state.value.items.single { it.id == "shared" }.title)
        }

    @Test
    fun `expired items are hidden, also once the clock passes their expiry`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        reader.broadcast(item("gone", expiresAt = clock.instant()), item("soon", expiresAt = clock.instant().plusSeconds(60)))
        reader.personal()
        assertEquals(listOf("soon"), sut.state.value.items.map { it.id })

        clock.advance(61)
        sut.reevaluateExpiry()

        assertTrue(sut.state.value.items.isEmpty())
    }

    @Test
    fun `an empty inbox is loaded, not failed`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        reader.broadcast()
        reader.personal()

        assertFalse(sut.state.value.isLoading)
        assertNull(sut.state.value.error)
        assertFalse(sut.state.value.hasPartialFailure)
    }

    @Test
    fun `both sources failing shows the error`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        reader.failBroadcast()
        reader.failPersonal()

        assertFalse(sut.state.value.isLoading)
        assertEquals(InboxError.Unavailable.userMessage, sut.state.value.error)
    }

    @Test
    fun `one source failing keeps the other's items and flags it`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        reader.failBroadcast()
        reader.personal(item("p1", source = InboxSource.PERSONAL))

        assertEquals(listOf("p1"), sut.state.value.items.map { it.id })
        assertTrue(sut.state.value.hasPartialFailure)
    }

    @Test
    fun `a later failure sets the flag and a retry recovers`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        reader.broadcast(item("a1"))
        reader.personal()
        reader.failPersonal()
        assertTrue(sut.state.value.hasPartialFailure)

        reader.personal()
        sut.retry()

        assertFalse(sut.state.value.hasPartialFailure)
        assertEquals(listOf("a1"), sut.state.value.items.map { it.id })
    }

    @Test
    fun `retry restarts from loading and reopens every listener`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        reader.failBroadcast()
        reader.failPersonal()
        assertEquals(1, reader.broadcastOpens)

        reader.resetSources()
        sut.retry()

        assertTrue(sut.state.value.isLoading)
        assertEquals(2, reader.broadcastOpens)
    }

    @Test
    fun `a failed source stays failed until a retry`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        reader.failBroadcast()
        reader.personal()

        reader.broadcast(item("late"))

        assertTrue(sut.state.value.hasPartialFailure)
        assertTrue(sut.state.value.items.isEmpty())
    }

    // --- Unread ---

    @Test
    fun `nothing is unread without a marker`() = runTest(UnconfinedTestDispatcher()) {
        val sut = loaded(item("a1"))

        assertEquals(ReadMarker.Unknown, sut.state.value.readMarker)
        assertEquals(0, sut.state.value.unreadCount)
    }

    @Test
    fun `entries newer than the marker are unread, undated never, and nothing is written`() =
        runTest(UnconfinedTestDispatcher()) {
            val sut = loaded(item("new", t0.plusSeconds(1)), item("old", t0.minusSeconds(1)), item("undated", null))

            reader.marker(t0)

            assertEquals(1, sut.state.value.unreadCount)
            assertTrue(sut.state.value.isUnread(sut.state.value.items.first { it.id == "new" }))
            assertEquals(0, reader.markReadCount)
        }

    @Test
    fun `an entry at the marker's instant is read`() = runTest(UnconfinedTestDispatcher()) {
        val sut = loaded(item("same", t0))

        reader.marker(t0)

        assertEquals(0, sut.state.value.unreadCount)
    }

    @Test
    fun `personal entries count as unread`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        reader.broadcast()
        reader.personal(item("p1", t0.plusSeconds(1), InboxSource.PERSONAL))

        reader.marker(t0)

        assertEquals(1, sut.unreadCount.value)
    }

    @Test
    fun `an unreadable marker counts nothing and writes nothing`() = runTest(UnconfinedTestDispatcher()) {
        val sut = loaded(item("a1", t0.plusSeconds(1)))

        reader.failMarker()
        sut.markAllRead()

        assertEquals(ReadMarker.Unavailable, sut.state.value.readMarker)
        assertEquals(0, sut.state.value.unreadCount)
        assertEquals(0, reader.markReadCount)
    }

    @Test
    fun `a marker written on another device updates the count live`() = runTest(UnconfinedTestDispatcher()) {
        val sut = loaded(item("a1", t0.plusSeconds(1)))
        reader.marker(t0)
        assertEquals(1, sut.unreadCount.value)

        reader.marker(t0.plusSeconds(5))

        assertEquals(0, sut.unreadCount.value)
    }

    // --- Marking read ---

    @Test
    fun `marking read twice writes once and the count drops`() = runTest(UnconfinedTestDispatcher()) {
        val sut = loaded(item("a1", t0.plusSeconds(1)))
        reader.marker(t0)

        sut.markAllRead()
        sut.markAllRead()

        assertEquals(1, reader.markReadCount)
        assertEquals(0, sut.unreadCount.value)
    }

    @Test
    fun `nothing unread means no write`() = runTest(UnconfinedTestDispatcher()) {
        val sut = loaded(item("a1", t0.minusSeconds(1)))
        reader.marker(t0)

        sut.markAllRead()

        assertEquals(0, reader.markReadCount)
    }

    @Test
    fun `a newer arrival after marking is written again`() = runTest(UnconfinedTestDispatcher()) {
        reader.echoMarkRead = false
        val sut = loaded(item("a1", t0.plusSeconds(1)))
        reader.marker(t0)
        sut.markAllRead()

        reader.broadcast(item("a1", t0.plusSeconds(1)), item("a2", t0.plusSeconds(2)))
        sut.markAllRead()

        assertEquals(2, reader.markReadCount)
    }

    @Test
    fun `a failed write is retried at the next call`() = runTest(UnconfinedTestDispatcher()) {
        val sut = loaded(item("a1", t0.plusSeconds(1)))
        reader.marker(t0)
        reader.markReadFailure = IOException("offline")
        sut.markAllRead()
        assertEquals(1, sut.unreadCount.value)

        reader.markReadFailure = null
        sut.markAllRead()

        assertEquals(2, reader.markReadCount)
        assertEquals(0, sut.unreadCount.value)
    }

    // --- A new account (no marker on the server) ---

    @Test
    fun `a new account starts read and writes its marker once`() = runTest(UnconfinedTestDispatcher()) {
        val sut = loaded(item("a1", t0))

        reader.markerMissing()

        assertEquals(listOf("a1"), sut.state.value.items.map { it.id })
        assertEquals(0, sut.unreadCount.value)
        assertEquals(1, reader.markReadCount)
        sut.markAllRead()
        assertEquals(1, reader.markReadCount)
    }

    @Test
    fun `on a new account only later entries are unread`() = runTest(UnconfinedTestDispatcher()) {
        val sut = loaded(item("a1", t0))
        reader.echoMarkRead = false
        reader.markerMissing()

        reader.broadcast(item("a1", t0), item("later", clock.instant().plusSeconds(1)))

        assertEquals(1, sut.unreadCount.value)
    }

    @Test
    fun `a repeated missing marker while the write is pending writes nothing more and never flashes unread`() =
        runTest(UnconfinedTestDispatcher()) {
            val sut = loaded(item("a1", t0))
            reader.echoMarkRead = false

            reader.markerMissing()
            reader.markerMissing()

            assertEquals(1, reader.markReadCount)
            assertEquals(0, sut.unreadCount.value)
        }

    @Test
    fun `a marker read error never writes`() = runTest(UnconfinedTestDispatcher()) {
        loaded(item("a1"))

        reader.failMarker()

        assertEquals(0, reader.markReadCount)
    }

    @Test
    fun `a marker that has not arrived never writes`() = runTest(UnconfinedTestDispatcher()) {
        val sut = loaded(item("a1", t0.plusSeconds(1)))

        sut.markAllRead()

        assertEquals(0, reader.markReadCount)
    }

    @Test
    fun `after a sign-out the next new account writes its own marker`() = runTest(UnconfinedTestDispatcher()) {
        loaded(item("a1"))
        reader.markerMissing()

        sessions.value = SessionState.SignedOut
        sessions.value = signedIn("u-2")
        reader.markerMissing()

        assertEquals(listOf("u-1", "u-2"), reader.markReadUids)
    }

    @Test
    fun `the legacy read ids are dropped once the first marker is written`() = runTest(UnconfinedTestDispatcher()) {
        val sut = loaded(item("a1", t0))

        reader.markerMissing()

        assertEquals(1, reader.markReadCount)
        assertFalse(legacy.hasLegacyIds)
        assertEquals(0, sut.unreadCount.value)
    }

    @Test
    fun `an existing marker skips the migration`() = runTest(UnconfinedTestDispatcher()) {
        loaded(item("a1"))

        reader.marker(t0)

        assertEquals(0, reader.markReadCount)
        assertTrue(legacy.hasLegacyIds)
    }

    @Test
    fun `a failed first marker keeps the legacy read ids`() = runTest(UnconfinedTestDispatcher()) {
        loaded(item("a1"))
        reader.markReadFailure = IOException("offline")

        reader.markerMissing()

        assertTrue(legacy.hasLegacyIds)
        assertEquals(0, legacy.clearCount)
    }

    @Test
    fun `signing out forgets everything and ignores the old user's late deliveries`() = runTest(UnconfinedTestDispatcher()) {
        val sut = loaded(item("a1", t0.plusSeconds(1)))
        reader.marker(t0)

        sessions.value = SessionState.SignedOut
        reader.broadcast(item("late"))
        reader.marker(t0.minusSeconds(60))

        assertEquals(InboxState(), sut.state.value)
        assertEquals(0, sut.unreadCount.value)
    }

    // --- Helpers ---

    private fun TestScope.loaded(vararg broadcast: InboxItem): InboxStore = sut().also {
        reader.broadcast(*broadcast)
        reader.personal()
    }

    private class MutableClock(private var now: Instant) : Clock() {
        fun advance(seconds: Long) {
            now = now.plusSeconds(seconds)
        }

        override fun instant(): Instant = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?): Clock = this
    }

    /** Sources driven by the test; a failure ends that source's flow, as the real listeners do. */
    private inner class ScriptedInboxReader : InboxReader {

        private var broadcastEvents = MutableSharedFlow<List<InboxItem>?>(replay = 1)
        private var personalEvents = MutableSharedFlow<List<InboxItem>?>(replay = 1)
        private val markerEvents = MutableSharedFlow<Marker>(replay = 1)

        var broadcastOpens = 0
            private set
        var markReadCount = 0
            private set
        val markReadUids = mutableListOf<String>()
        var markReadFailure: Exception? = null

        /** The server echoes the written marker back (as Firestore does), unless switched off. */
        var echoMarkRead = true

        fun broadcast(vararg items: InboxItem) = broadcastEvents.tryEmit(items.toList())
        fun personal(vararg items: InboxItem) = personalEvents.tryEmit(items.toList())
        fun failBroadcast() = broadcastEvents.tryEmit(null)
        fun failPersonal() = personalEvents.tryEmit(null)
        fun marker(readAt: Instant) = markerEvents.tryEmit(Marker.At(readAt))
        fun markerMissing() = markerEvents.tryEmit(Marker.Missing)
        fun failMarker() = markerEvents.tryEmit(Marker.Failed)

        fun resetSources() {
            broadcastEvents = MutableSharedFlow(replay = 1)
            personalEvents = MutableSharedFlow(replay = 1)
        }

        override fun broadcastChanges(): Flow<List<InboxItem>> =
            broadcastEvents.map { it ?: throw InboxError.Unavailable }.onStart { broadcastOpens++ }

        override fun personalChanges(uid: String): Flow<List<InboxItem>> =
            personalEvents.map { it ?: throw InboxError.Unavailable }

        override fun readMarkerChanges(uid: String): Flow<Instant?> = markerEvents.map {
            when (it) {
                is Marker.At -> it.readAt
                Marker.Missing -> null
                Marker.Failed -> throw InboxError.Unavailable
            }
        }

        override suspend fun markRead(uid: String) {
            markReadCount++
            markReadUids += uid
            markReadFailure?.let { throw it }
            if (echoMarkRead) markerEvents.tryEmit(Marker.At(clock.instant()))
        }

        override fun imageUrl(storagePath: String): String? = null
    }

    private sealed interface Marker {
        data class At(val readAt: Instant) : Marker
        data object Missing : Marker
        data object Failed : Marker
    }
}
