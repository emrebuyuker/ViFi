package com.salticusteam.vifi.feature.conversionadmin

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.BulkConversionScope
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ExamConversionError
import com.salticusteam.vifi.feature.conversionadmin.StubExamConversionService.Call
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** ADX-03 scope picker, ADX-04 "Yeni yapıya geçir" and ADX-05 published-question admin menu view models. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversionAdminActionsViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val service = StubExamConversionService()

    private fun auth(isAdmin: Boolean) = FakeAuthService(latency = Duration.ZERO, isAdmin = isAdmin)

    // --- Bulk scope picker (iOS BulkScopeLevel) ---

    @Test
    fun `the scope picker walks universities, faculties and departments, and a department can be picked whole`() = runTest {
        val sut = BulkScopePickerViewModel(FakeArchiveRepository())
        advanceUntilIdle()
        assertTrue(sut.uiState.value.path.isRoot)
        val university = sut.uiState.value.items.orEmpty().first()
        assertFalse(sut.uiState.value.canPickDepartment)

        sut.open(university)
        advanceUntilIdle()
        val faculty = sut.uiState.value.items.orEmpty().first()
        sut.open(faculty)
        advanceUntilIdle()
        assertFalse(sut.uiState.value.canPickDepartment)
        val department = sut.uiState.value.items.orEmpty().first()
        sut.open(department)
        advanceUntilIdle()

        assertTrue(sut.uiState.value.canPickDepartment)
        val (scope, name) = sut.uiState.value.departmentScope() ?: error("A department level has a scope")
        assertEquals(BulkConversionScope.Department(department.path.components.last()), scope)
        assertEquals(department.path.displayNames.joinToString(" › "), name)
        // Its entries are courses: they are picked, not opened.
        val lesson = sut.uiState.value.items.orEmpty().first()
        assertEquals(ArchiveLevel.LESSON, lesson.level)
        val before = sut.uiState.value.path
        sut.open(lesson)
        advanceUntilIdle()
        assertEquals(before, sut.uiState.value.path)
    }

    @Test
    fun `the scope picker goes up one level and closes at the root`() = runTest {
        val sut = BulkScopePickerViewModel(FakeArchiveRepository())
        advanceUntilIdle()
        sut.open(sut.uiState.value.items.orEmpty().first())
        advanceUntilIdle()
        assertEquals(1, sut.uiState.value.path.depth)

        assertTrue(sut.up())
        advanceUntilIdle()
        assertTrue(sut.uiState.value.path.isRoot)
        assertFalse(sut.up())
    }

    @Test
    fun `the scope picker shows a load failure and retries`() = runTest {
        val archive = FakeArchiveRepository(error = ArchiveError.Offline)
        val sut = BulkScopePickerViewModel(archive)
        advanceUntilIdle()
        assertNotNull(sut.uiState.value.error)
        assertNull(sut.uiState.value.items)

        archive.error = null
        sut.retry()
        advanceUntilIdle()
        assertNull(sut.uiState.value.error)
        assertEquals(2, sut.uiState.value.items?.size)
        assertEquals(ArchivePath.ROOT, sut.uiState.value.path)
    }

    // --- "Yeni yapıya geçir" (iOS ExamConversionAdminButton) ---

    private fun button(isAdmin: Boolean) = ExamConversionButtonViewModel("ex1", service, auth(isAdmin))

    @Test
    fun `the convert button is hidden until the admin claim is known and true`() = runTest {
        val member = button(isAdmin = false)
        val admin = button(isAdmin = true)
        assertFalse(admin.uiState.value.isAdmin)

        advanceUntilIdle()

        assertFalse(member.uiState.value.isAdmin)
        assertTrue(admin.uiState.value.isAdmin)
    }

    @Test
    fun `converting queues the exam with the picked provider and shows the job`() = runTest {
        val sut = button(isAdmin = true)
        advanceUntilIdle()

        sut.start(AIProvider.GEMINI)
        advanceUntilIdle()

        assertEquals(listOf<Call>(Call.RequestConversion("ex1", AIProvider.GEMINI)), service.calls)
        assertEquals(ConversionStatus.QUEUED, sut.uiState.value.job?.status)
        assertEquals("exam-ex1", sut.uiState.value.job?.id)
        assertFalse(sut.uiState.value.isWorking)
    }

    @Test
    fun `a failed conversion request shows the message`() = runTest {
        val sut = button(isAdmin = true)
        advanceUntilIdle()
        service.error = ExamConversionError.BudgetExceeded

        sut.start(AIProvider.CLAUDE)
        advanceUntilIdle()

        assertEquals(ExamConversionError.BudgetExceeded.userMessage, sut.uiState.value.error)
        assertNull(sut.uiState.value.job)
        assertFalse(sut.uiState.value.isWorking)
    }

    // --- Published question menu (iOS PublishedQuestionAdminActions) ---

    private fun menu(isAdmin: Boolean = true) = PublishedQuestionAdminViewModel(service, auth(isAdmin))

    @Test
    fun `the menu is for admins only`() = runTest {
        val member = menu(isAdmin = false)
        val admin = menu()
        advanceUntilIdle()

        assertFalse(member.uiState.value.isAdmin)
        assertTrue(admin.uiState.value.isAdmin)
    }

    @Test
    fun `saving an edit updates the published question and tells the host`() = runTest {
        val sut = menu()
        advanceUntilIdle()

        sut.events.test {
            val message = sut.saveEdit(ConversionFixture.question(order = 2))

            assertNull(message)
            assertEquals(PublishedQuestionAdminEvent.Changed, awaitItem())
        }
        assertEquals(listOf<Call>(Call.UpdatePublishedQuestion("002")), service.calls)
    }

    @Test
    fun `a failed edit returns the message for the editor and does not tell the host`() = runTest {
        val sut = menu()
        advanceUntilIdle()
        service.error = ExamConversionError.PermissionDenied

        sut.events.test {
            val message = sut.saveEdit(ConversionFixture.question())

            assertEquals(ExamConversionError.PermissionDenied.userMessage, message)
            expectNoEvents()
        }
    }

    @Test
    fun `regenerating sends the provider, shows the hint and tells the host`() = runTest {
        val sut = menu()
        advanceUntilIdle()

        sut.events.test {
            sut.regenerate(ConversionFixture.question(order = 1), AIProvider.GEMINI)
            advanceUntilIdle()

            assertEquals(PublishedQuestionAdminEvent.Changed, awaitItem())
        }
        assertEquals(listOf<Call>(Call.RegeneratePublishedQuestion("001", AIProvider.GEMINI)), service.calls)
        assertEquals(PublishedQuestionMessage.Regenerating, sut.uiState.value.message)
        assertFalse(sut.uiState.value.isWorking)

        sut.onMessageDismissed()
        assertNull(sut.uiState.value.message)
    }

    @Test
    fun `a failed regeneration shows the failure`() = runTest {
        val sut = menu()
        advanceUntilIdle()
        service.error = ExamConversionError.NotAllowed("Soru bu sürümde yeniden üretilemez.")

        sut.regenerate(ConversionFixture.question(), AIProvider.CLAUDE)
        advanceUntilIdle()

        assertEquals(PublishedQuestionMessage.Failure("Soru bu sürümde yeniden üretilemez."), sut.uiState.value.message)
        assertFalse(sut.uiState.value.isWorking)
    }
}
