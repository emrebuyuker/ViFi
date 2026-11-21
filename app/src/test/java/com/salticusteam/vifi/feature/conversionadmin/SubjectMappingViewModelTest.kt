package com.salticusteam.vifi.feature.conversionadmin

import com.salticusteam.vifi.core.data.repository.SubjectMappingService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubjectMappingService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubjectMappingService.Call
import com.salticusteam.vifi.core.model.AdminSubject
import com.salticusteam.vifi.core.model.ExamConversionError
import com.salticusteam.vifi.core.model.SubjectCourse
import com.salticusteam.vifi.core.model.SubjectTarget
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** iOS `SubjectMappingTests` › `SubjectMappingViewModel`. */
@OptIn(ExperimentalCoroutinesApi::class)
class SubjectMappingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun course(id: String, name: String = "Ders", university: String = "ege", questions: Int = 5) = SubjectCourse(
        id = id, name = name, universityId = university, universityName = university.replaceFirstChar { it.uppercase() },
        facultyName = "Mühendislik", departmentName = "Bilgisayar", questionCount = questions,
    )

    private fun subject(id: String, name: String? = null, courses: List<SubjectCourse> = emptyList()) =
        AdminSubject(id = id, name = name ?: id.replaceFirstChar { it.uppercase() }, aliases = listOf(id), courses = courses)

    private val algorithms = subject("algoritmalar", "Algoritmalar", listOf(course("c1", "Algoritmalar", "ege", 10)))
    private val algorithm = subject("algoritma", "Algoritma", listOf(course("c2", "Algoritma", "itu", 4)))
    private val physics = subject("fizik", "Fizik", listOf(course("c3", "Fizik", "ege"), course("c4", "Fizik", "itu")))

    private fun service() = FakeSubjectMappingService(subjectList = listOf(algorithms, algorithm, physics))

    @Test
    fun `loading shows the subjects and the possible matches of single-university subjects`() = runTest {
        val service = service()
        val sut = SubjectMappingViewModel(service)

        advanceUntilIdle()

        val state = sut.uiState.value
        assertEquals(listOf("algoritmalar", "algoritma", "fizik"), state.subjects?.map { it.id })
        assertEquals(1, service.listCount)
        assertEquals("algoritmalar", state.possibleMatch("algoritma")?.id)
        assertEquals("algoritma", state.possibleMatch("algoritmalar")?.id)
        assertNull(state.possibleMatch("fizik"))
    }

    @Test
    fun `a failed load shows the message and refresh recovers`() = runTest {
        val service = service()
        service.error = ExamConversionError.Offline
        val sut = SubjectMappingViewModel(service)

        advanceUntilIdle()
        assertEquals(ExamConversionError.Offline.userMessage, sut.uiState.value.loadError)
        assertNull(sut.uiState.value.subjects)

        service.error = null
        sut.refresh()
        advanceUntilIdle()
        assertEquals(3, sut.uiState.value.subjects?.size)
        assertNull(sut.uiState.value.loadError)
    }

    @Test
    fun `search matches subject and course names, ignoring case and diacritics`() = runTest {
        val service = FakeSubjectMappingService(
            subjectList = listOf(algorithms, subject("veri", "Veri Yapıları", listOf(course("c9", "Çizge Kuramı")))),
        )
        val sut = SubjectMappingViewModel(service)
        advanceUntilIdle()

        sut.onSearchChange("yapilari")
        assertEquals(listOf("veri"), sut.uiState.value.filteredSubjects.map { it.id })
        sut.onSearchChange("CIZGE")
        assertEquals(listOf("veri"), sut.uiState.value.filteredSubjects.map { it.id })
        sut.onSearchChange("  ")
        assertEquals(2, sut.uiState.value.filteredSubjects.size)
        sut.onSearchChange("yok")
        assertTrue(sut.uiState.value.filteredSubjects.isEmpty())
    }

    @Test
    fun `changing a course's subject calls the service and reloads the list`() = runTest {
        val service = service()
        val sut = SubjectMappingViewModel(service)
        advanceUntilIdle()
        val moved = subject("algoritmalar", "Algoritmalar", algorithms.courses + algorithm.courses)
        service.responses += listOf(moved, physics)

        sut.setCourseSubject(algorithm.courses[0], SubjectTarget.Existing("algoritmalar"))
        advanceUntilIdle()

        assertEquals(listOf<Call>(Call.SetCourseSubject("c2", SubjectTarget.Existing("algoritmalar"))), service.calls)
        assertEquals(listOf("algoritmalar", "fizik"), sut.uiState.value.subjects?.map { it.id })
        assertNull(sut.uiState.value.subject("algoritma"))
        assertFalse(sut.uiState.value.isWorking)
    }

    @Test
    fun `a new subject name is passed through`() = runTest {
        val service = service()
        val sut = SubjectMappingViewModel(service)
        advanceUntilIdle()

        sut.setCourseSubject(physics.courses[0], SubjectTarget.New("Genel Fizik"))
        advanceUntilIdle()

        assertEquals(listOf<Call>(Call.SetCourseSubject("c3", SubjectTarget.New("Genel Fizik"))), service.calls)
    }

    @Test
    fun `rename trims the name and ignores blank or unchanged ones`() = runTest {
        val service = service()
        val sut = SubjectMappingViewModel(service)
        advanceUntilIdle()

        sut.rename(physics, "  Genel Fizik ")
        advanceUntilIdle()
        sut.rename(physics, "   ")
        sut.rename(physics, "Fizik")
        advanceUntilIdle()

        assertEquals(listOf<Call>(Call.Rename("fizik", "Genel Fizik")), service.calls)
    }

    @Test
    fun `merge calls the service, reloads, and refuses merging a subject into itself`() = runTest {
        val service = service()
        val sut = SubjectMappingViewModel(service)
        advanceUntilIdle()
        service.responses += listOf(subject("algoritmalar", courses = algorithms.courses + algorithm.courses), physics)

        sut.merge(algorithms, algorithms)
        advanceUntilIdle()
        assertTrue(service.calls.isEmpty())

        sut.merge(algorithm, algorithms)
        advanceUntilIdle()

        assertEquals(listOf<Call>(Call.Merge("algoritma", "algoritmalar")), service.calls)
        assertNull(sut.uiState.value.subject("algoritma"))
        assertTrue(sut.uiState.value.possibleMatches.isEmpty())
    }

    @Test
    fun `a failed action keeps the list, shows the error, and does not reload`() = runTest {
        val service = service()
        val sut = SubjectMappingViewModel(service)
        advanceUntilIdle()
        service.actionError = ExamConversionError.PermissionDenied

        sut.merge(algorithm, algorithms)
        advanceUntilIdle()

        assertEquals(ExamConversionError.PermissionDenied.userMessage, sut.uiState.value.actionError)
        assertEquals(3, sut.uiState.value.subjects?.size)
        assertEquals(1, service.listCount)
        assertFalse(sut.uiState.value.isWorking)

        sut.clearActionError()
        assertNull(sut.uiState.value.actionError)
    }

    @Test
    fun `a reload failure after a successful action keeps the old list and reports the error`() = runTest {
        // The action goes through, then the listing fails (offline right after the change).
        val flaky = object : SubjectMappingService {
            var listingFails = false
            override suspend fun subjects(): List<AdminSubject> {
                if (listingFails) throw ExamConversionError.Offline
                return listOf(algorithms, algorithm, physics)
            }
            override suspend fun setCourseSubject(courseId: String, target: SubjectTarget) = Unit
            override suspend fun renameSubject(id: String, name: String) {
                listingFails = true
            }
            override suspend fun mergeSubject(id: String, into: String) = Unit
        }
        val sut = SubjectMappingViewModel(flaky)
        advanceUntilIdle()

        sut.rename(physics, "Genel Fizik")
        advanceUntilIdle()

        assertEquals(3, sut.uiState.value.subjects?.size)
        assertEquals(ExamConversionError.Offline.userMessage, sut.uiState.value.actionError)
        assertFalse(sut.uiState.value.isWorking)
    }
}
