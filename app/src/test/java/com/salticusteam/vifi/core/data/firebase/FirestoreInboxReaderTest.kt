package com.salticusteam.vifi.core.data.firebase

import app.cash.turbine.test
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.storage.FirebaseStorage
import com.salticusteam.vifi.core.model.InboxError
import com.salticusteam.vifi.core.model.InboxSource
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The inbox's Firestore sources (iOS `FirebaseInboxReader`, appendix A.2–A.3) against mocked SDK objects: the paths and
 * limits the shared rules and indexes expect, the exact marker write, and the listeners' errors and clean-up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreInboxReaderTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val storage = mockk<FirebaseStorage> {
        every { reference } returns mockk { every { bucket } returns BUCKET }
    }
    private val reader = FirestoreInboxReader(firestore, storage)

    private val users = mockk<CollectionReference>()
    private val user = mockk<DocumentReference>()
    private val settings = mockk<CollectionReference>()
    private val marker = mockk<DocumentReference>()

    init {
        every { firestore.collection("users") } returns users
        every { users.document(UID) } returns user
        every { user.collection("settings") } returns settings
        every { settings.document("inbox") } returns marker
    }

    // --- The two lists ---

    @Test
    fun `the broadcast reads the newest 50 announcements newest first, and drops what does not parse`() =
        runTest(UnconfinedTestDispatcher()) {
            val list = stubAnnouncements()

            reader.broadcastChanges().test {
                list.listener.captured.onEvent(snapshot(announcement("a1"), untitled("a2")), null)

                val items = awaitItem()
                assertEquals(listOf("a1"), items.map { it.id })
                assertEquals(InboxSource.BROADCAST, items.single().source)
                assertEquals(Instant.ofEpochSecond(CREATED_SECONDS, 500), items.single().createdAt)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the personal inbox reads the user's newest 100`() = runTest(UnconfinedTestDispatcher()) {
        val inbox = mockk<CollectionReference>()
        every { user.collection("inbox") } returns inbox
        val list = stubList(inbox, limit = 100)

        reader.personalChanges(UID).test {
            list.listener.captured.onEvent(snapshot(announcement("p-exam")), null)

            assertEquals(listOf("p-exam" to InboxSource.PERSONAL), awaitItem().map { it.id to it.source })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a list that cannot be read ends with Unavailable and removes its listener`() = runTest(UnconfinedTestDispatcher()) {
        val list = stubAnnouncements()

        reader.broadcastChanges().test {
            list.listener.captured.onEvent(null, mockk<FirebaseFirestoreException>(relaxed = true))

            assertSame(InboxError.Unavailable, awaitError())
        }
        verify(exactly = 1) { list.registration.remove() }
    }

    @Test
    fun `leaving the inbox removes the listener`() = runTest(UnconfinedTestDispatcher()) {
        val list = stubAnnouncements()

        reader.broadcastChanges().test {
            verify(exactly = 0) { list.registration.remove() }
            cancelAndIgnoreRemainingEvents()
        }
        verify(exactly = 1) { list.registration.remove() }
    }

    // --- The read marker ---

    @Test
    fun `the marker follows metadata changes and reads a pending write as its estimate`() =
        runTest(UnconfinedTestDispatcher()) {
            val listener = stubMarker()

            reader.readMarkerChanges(UID).test {
                listener.captured.onEvent(markerSnapshot(readAt = Timestamp(CREATED_SECONDS, 0)), null)

                assertEquals(Instant.ofEpochSecond(CREATED_SECONDS), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a marker missing only from the cache emits nothing, the server's missing marker emits null`() =
        runTest(UnconfinedTestDispatcher()) {
            val listener = stubMarker()

            reader.readMarkerChanges(UID).test {
                listener.captured.onEvent(markerSnapshot(readAt = null, fromCache = true), null)
                expectNoEvents()

                listener.captured.onEvent(markerSnapshot(readAt = null, fromCache = false), null)
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a marker that cannot be read ends with Unavailable and removes its listener`() =
        runTest(UnconfinedTestDispatcher()) {
            val registration = mockk<ListenerRegistration>(relaxed = true)
            val listener = stubMarker(registration)

            reader.readMarkerChanges(UID).test {
                listener.captured.onEvent(null, mockk<FirebaseFirestoreException>(relaxed = true))

                assertSame(InboxError.Unavailable, awaitError())
            }
            verify(exactly = 1) { registration.remove() }
        }

    @Test
    fun `marking read writes exactly readAt as the server's time, a full set`() = runTest {
        val written = slot<Any>()
        every { marker.set(capture(written)) } returns Tasks.forResult(null)

        reader.markRead(UID)

        assertEquals(mapOf("readAt" to FieldValue.serverTimestamp()), written.captured)
    }

    // --- Images ---

    @Test
    fun `only a notification image gets a download URL`() {
        assertEquals(
            "https://firebasestorage.googleapis.com/v0/b/$BUCKET/o/notificationImages%2Fabc-1.jpg?alt=media",
            reader.imageUrl("notificationImages/abc-1.jpg"),
        )
        assertNull(reader.imageUrl("exams/x/1.jpg"))
        assertNull(reader.imageUrl("notificationImages/../exams/x.jpg"))
        assertNull(reader.imageUrl(""))
    }

    // Helpers
    private class ListStub(val listener: CapturingSlot<EventListener<QuerySnapshot>>, val registration: ListenerRegistration)

    private fun stubAnnouncements(): ListStub {
        val announcements = mockk<CollectionReference>()
        every { firestore.collection("announcements") } returns announcements
        return stubList(announcements, limit = 50)
    }

    private fun stubList(collection: CollectionReference, limit: Long): ListStub {
        val ordered = mockk<Query>()
        val limited = mockk<Query>()
        val listener = slot<EventListener<QuerySnapshot>>()
        val registration = mockk<ListenerRegistration>(relaxed = true)
        every { collection.orderBy("createdAt", Query.Direction.DESCENDING) } returns ordered
        every { ordered.limit(limit) } returns limited
        every { limited.addSnapshotListener(capture(listener)) } returns registration
        return ListStub(listener, registration)
    }

    private fun stubMarker(
        registration: ListenerRegistration = mockk(relaxed = true),
    ): CapturingSlot<EventListener<DocumentSnapshot>> {
        val listener = slot<EventListener<DocumentSnapshot>>()
        every { marker.addSnapshotListener(MetadataChanges.INCLUDE, capture(listener)) } returns registration
        return listener
    }

    private fun snapshot(vararg documents: DocumentSnapshot) =
        mockk<QuerySnapshot> { every { this@mockk.documents } returns documents.toList() }

    private fun announcement(id: String) = document(
        id,
        mapOf("title" to "Duyuru", "body" to "Mesaj", "createdAt" to Timestamp(CREATED_SECONDS, 500)),
    )

    private fun untitled(id: String) = document(id, mapOf("body" to "Başlıksız"))

    private fun document(id: String, data: Map<String, Any?>) = mockk<DocumentSnapshot> {
        every { this@mockk.id } returns id
        every { this@mockk.data } returns data
    }

    private fun markerSnapshot(readAt: Timestamp?, fromCache: Boolean = false) = mockk<DocumentSnapshot> {
        every { metadata } returns mockk<SnapshotMetadata> { every { isFromCache } returns fromCache }
        every { exists() } returns (readAt != null)
        every { getTimestamp("readAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) } returns readAt
    }

    private companion object {
        const val UID = "user-1"
        const val BUCKET = "vifi-831a8.appspot.com"
        const val CREATED_SECONDS = 1_761_800_000L
    }
}
