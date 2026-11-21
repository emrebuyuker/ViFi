package com.salticusteam.vifi.feature.moderation

import com.salticusteam.vifi.core.data.repository.SubmitterProfileReader
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubmitterProfileReader
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.model.ProfileError
import com.salticusteam.vifi.core.model.SubmitterInfo
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `SubmitterProfileTests` (the lookup half; the parser cases are in `SubmitterInfoParserTest`). */
@OptIn(ExperimentalCoroutinesApi::class)
class SubmitterDirectoryTest {

    private val info = SubmitterInfo(
        fullName = "Ayşe Demir",
        university = "Boğaziçi Üniversitesi",
        faculty = "Mühendislik Fakültesi",
        department = "Bilgisayar Mühendisliği",
    )

    /**
     * A directory whose session follower already runs. `runCurrent`, not `advanceUntilIdle`: the latter does not wait
     * for work that lives only in `backgroundScope`.
     */
    private fun TestScope.directory(
        reader: SubmitterProfileReader,
        auth: FakeAuthService = FakeAuthService(latency = Duration.ZERO),
    ) = SubmitterDirectory(reader, SessionStore(auth, backgroundScope), backgroundScope).also { runCurrent() }

    // K4: submitters' personal data does not outlive the admin's session.
    @Test
    fun `signing out clears the cache and discards a read still in flight`() = runTest {
        val auth = FakeAuthService(latency = Duration.ZERO)
        val gate = CompletableDeferred<Unit>()
        val reader = object : SubmitterProfileReader {
            override suspend fun submitter(uid: String): SubmitterInfo? {
                if (uid == "slow") gate.await()
                return info
            }
        }
        val sut = directory(reader, auth)
        sut.load("u1")
        launch { sut.load("slow") }
        runCurrent()
        assertEquals(SubmitterState.Loaded(info), sut.submitters.value["u1"])
        assertEquals(SubmitterState.Loading, sut.submitters.value["slow"])

        auth.signOut()
        runCurrent()
        assertEquals(emptyMap<String, SubmitterState>(), sut.submitters.value)
        gate.complete(Unit)
        runCurrent()

        assertEquals(emptyMap<String, SubmitterState>(), sut.submitters.value)
    }

    @Test
    fun `the same uid is read once however many rows ask`() = runTest {
        val reader = FakeSubmitterProfileReader(profiles = mapOf("u1" to info))
        val sut = directory(reader)

        sut.load("u1")
        sut.load("u1")

        assertEquals(listOf("u1"), reader.readUids)
        assertEquals(SubmitterState.Loaded(info), sut.submitters.value["u1"])
    }

    @Test
    fun `concurrent asks for the same uid read it once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var reads = 0
        val reader = object : SubmitterProfileReader {
            override suspend fun submitter(uid: String): SubmitterInfo? {
                reads++
                gate.await()
                return info
            }
        }
        val sut = directory(reader)

        launch { sut.load("u1") }
        launch { sut.load("u1") }
        advanceUntilIdle()
        assertEquals(SubmitterState.Loading, sut.submitters.value["u1"])
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, reads)
        assertEquals(SubmitterState.Loaded(info), sut.submitters.value["u1"])
    }

    @Test
    fun `a missing profile is reported as missing and not re-read`() = runTest {
        val reader = FakeSubmitterProfileReader(profiles = emptyMap())
        val sut = directory(reader)

        sut.load("gone")
        sut.load("gone")

        assertEquals(SubmitterState.Missing, sut.submitters.value["gone"])
        assertEquals(listOf("gone"), reader.readUids)
    }

    @Test
    fun `a read error is non-blocking and retried on the next ask`() = runTest {
        val reader = FakeSubmitterProfileReader(profiles = mapOf("u1" to info), error = ProfileError.Offline)
        val sut = directory(reader)

        sut.load("u1")
        assertEquals(SubmitterState.Failed, sut.submitters.value["u1"])

        reader.error = null
        sut.load("u1")

        assertEquals(SubmitterState.Loaded(info), sut.submitters.value["u1"])
        assertEquals(listOf("u1", "u1"), reader.readUids)
    }

    @Test
    fun `different uids are read separately`() = runTest {
        val reader = FakeSubmitterProfileReader(profiles = mapOf("a" to info))
        val sut = directory(reader)

        sut.load("a")
        sut.load("b")

        assertEquals(SubmitterState.Loaded(info), sut.submitters.value["a"])
        assertEquals(SubmitterState.Missing, sut.submitters.value["b"])
    }

    @Test
    fun `a cancelled read is forgotten so it is asked again`() = runTest {
        val reader = object : SubmitterProfileReader {
            override suspend fun submitter(uid: String): SubmitterInfo? = CompletableDeferred<SubmitterInfo?>().await()
        }
        val sut = directory(reader)

        val job = launch { sut.load("u1") }
        advanceUntilIdle()
        job.cancel()
        advanceUntilIdle()

        assertNull(sut.submitters.value["u1"])
    }

    @Test
    fun `affiliation joins university and department, skipping empty parts`() {
        assertEquals("Boğaziçi Üniversitesi · Bilgisayar Mühendisliği", info.affiliation())
        assertEquals("", SubmitterInfo("Ayşe Demir", "", "", "").affiliation())
        assertEquals("Bilgisayar Mühendisliği", info.copy(university = "").affiliation())
    }
}
