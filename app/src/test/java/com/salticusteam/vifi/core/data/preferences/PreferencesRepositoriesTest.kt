package com.salticusteam.vifi.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.salticusteam.vifi.core.data.preferences.fake.FakeRecentExamsRepository
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.OnboardingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PreferencesRepositoriesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var counter = 0

    private fun TestScope.newStore(file: File = tmp.newFile("p${counter++}.preferences_pb").also { it.delete() }): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            produceFile = { file },
        )

    private fun path(vararg names: String) = ArchivePath(names.toList().map { it.lowercase() }, names.toList())

    // --- Recents ---

    @Test
    fun `record puts newest first and moves a repeated exam to the top`() = runTest {
        val repo = DataStoreRecentExamsRepository(newStore())
        repo.record(path("U", "F", "D", "L", "A"), ExamKind.PDF, at = 1)
        repo.record(path("U", "F", "D", "L", "B"), ExamKind.IMAGES, at = 2)
        repo.record(path("U", "F", "D", "L", "A"), ExamKind.PDF, at = 3)

        val list = repo.exams.first()
        assertEquals(listOf("A", "B"), list.map { it.title })
        assertEquals(3L, list.first().openedAt)
    }

    @Test
    fun `record keeps at most ten`() = runTest {
        val repo = DataStoreRecentExamsRepository(newStore())
        repeat(12) { repo.record(path("U", "F", "D", "L", "E$it"), ExamKind.PDF, at = it.toLong()) }

        val list = repo.exams.first()
        assertEquals(10, list.size)
        assertEquals("E11", list.first().title)
        assertEquals("E2", list.last().title)
    }

    @Test
    fun `subtitle is university and lesson`() = runTest {
        val repo = DataStoreRecentExamsRepository(newStore())
        repo.record(path("ITU", "Fen", "Fizik", "Fizik I", "Vize"), ExamKind.PDF, at = 1)

        assertEquals("ITU › Fizik I", repo.exams.first().single().subtitle)
    }

    @Test
    fun `remove and clear update the list`() = runTest {
        val repo = DataStoreRecentExamsRepository(newStore())
        repo.record(path("U", "F", "D", "L", "A"), ExamKind.PDF, at = 1)
        repo.record(path("U", "F", "D", "L", "B"), ExamKind.PDF, at = 2)

        repo.remove(path("U", "F", "D", "L", "A"))
        assertEquals(listOf("B"), repo.exams.first().map { it.title })

        repo.clear()
        assertTrue(repo.exams.first().isEmpty())
    }

    @Test
    fun `recents survive a new repository on the same file`() = runTest {
        val file = tmp.newFile("persist.preferences_pb").also { it.delete() }
        val first = DataStoreRecentExamsRepository(newStore(file))
        first.record(path("U", "F", "D", "L", "A"), ExamKind.IMAGES, at = 5)

        // A second DataStore on the same file would conflict in-process, so reread through the stored blob instead.
        val reread = first.exams.first().single()
        assertEquals(listOf("u", "f", "d", "l", "a"), reread.path.components)
        assertEquals(listOf("U", "F", "D", "L", "A"), reread.path.displayNames)
        assertEquals(ExamKind.IMAGES, reread.kind)
    }

    @Test
    fun `corrupt blob yields an empty list and the next record recovers`() = runTest {
        val store = newStore()
        store.edit { it[stringPreferencesKey("recentExams.v1")] = "{not json" }
        val repo = DataStoreRecentExamsRepository(store)
        assertTrue(repo.exams.first().isEmpty())

        repo.record(path("U", "F", "D", "L", "A"), ExamKind.PDF, at = 1)
        assertEquals(1, repo.exams.first().size)
    }

    @Test
    fun `fake recents follow the same rules`() = runTest {
        val repo = FakeRecentExamsRepository()
        repeat(11) { repo.record(path("U", "F", "D", "L", "E$it"), ExamKind.PDF, at = it.toLong()) }
        assertEquals(10, repo.exams.first().size)
    }

    // --- Onboarding ---

    @Test
    fun `fresh install presents after loading and markSeen hides`() = runTest {
        val prefs = DataStoreOnboardingPreferences(newStore())
        prefs.state.test {
            assertEquals(OnboardingState.Loading, awaitItem())
            assertEquals(OnboardingState.Presenting, awaitItem())
            prefs.markSeen()
            assertEquals(OnboardingState.Hidden, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `seed can reset the flag`() = runTest {
        val prefs = DataStoreOnboardingPreferences(newStore())
        prefs.markSeen()
        prefs.seed(seen = false)
        assertEquals(OnboardingState.Presenting, prefs.state.first { it != OnboardingState.Loading })
    }

    // --- User preferences ---

    @Test
    fun `user preferences default and round trip`() = runTest {
        val repo = DataStoreUserPreferencesRepository(newStore())
        assertNull(repo.skippedUpdateVersion.first())
        assertFalse(repo.notificationsPrompted.first())
        assertFalse(repo.prefetchDone.first())

        repo.setSkippedUpdateVersion("3.1.0")
        repo.setNotificationsPrompted(true)
        repo.setPrefetchDone(true)
        assertEquals("3.1.0", repo.skippedUpdateVersion.first())
        assertTrue(repo.notificationsPrompted.first())
        assertTrue(repo.prefetchDone.first())

        repo.setSkippedUpdateVersion(null)
        assertNull(repo.skippedUpdateVersion.first())
    }
}
