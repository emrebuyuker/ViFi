package com.salticusteam.vifi.feature.browse

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeModerationService
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSummary
import com.salticusteam.vifi.core.model.ModerationError
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExamDeletionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val service = FakeModerationService(latency = Duration.ZERO)
    private val lesson = ArchivePath(listOf("itu", "muh", "blg", "blg101"))
    private val exam =
        ArchiveItem(lesson.appending("2023-vize", "2023 Vize"), ArchiveLevel.EXAM, null, ExamSummary(ExamKind.PDF, 1))
    private val lessonItem = ArchiveItem(lesson, ArchiveLevel.LESSON, childCount = 2, exam = null)

    private fun sut(isAdmin: Boolean) =
        ExamDeletionViewModel(FakeAuthService(isSignedIn = true, latency = Duration.ZERO, isAdmin = isAdmin), service)

    @Test
    fun `a non-admin is never offered the delete action`() = runTest {
        val sut = sut(isAdmin = false)
        advanceUntilIdle()

        sut.onDeleteRequested(exam)

        assertFalse(sut.uiState.value.isAdmin)
        assertFalse(sut.canDelete(exam))
        assertNull(sut.uiState.value.examToDelete)
    }

    @Test
    fun `an admin may delete exams only`() = runTest {
        val sut = sut(isAdmin = true)
        advanceUntilIdle()

        assertTrue(sut.canDelete(exam))
        assertFalse(sut.canDelete(lessonItem))
    }

    @Test
    fun `confirming deletes the exam and reports it`() = runTest {
        val sut = sut(isAdmin = true)
        advanceUntilIdle()

        sut.onDeleteRequested(exam)
        assertEquals(exam, sut.uiState.value.examToDelete)
        sut.events.test {
            sut.confirmDelete()
            assertEquals(ExamDeletionEvent.Deleted(exam), awaitItem())
        }

        assertEquals(listOf(exam.path), service.deletedExams)
        assertNull(sut.uiState.value.examToDelete)
        assertFalse(sut.uiState.value.isDeleting)
    }

    @Test
    fun `dismissing the confirmation deletes nothing`() = runTest {
        val sut = sut(isAdmin = true)
        advanceUntilIdle()

        sut.onDeleteRequested(exam)
        sut.onDeleteDismissed()
        sut.confirmDelete()
        advanceUntilIdle()

        assertTrue(service.deletedExams.isEmpty())
    }

    @Test
    fun `a failed deletion shows why until dismissed`() = runTest {
        service.failure = ModerationError.PermissionDenied
        val sut = sut(isAdmin = true)
        advanceUntilIdle()

        sut.onDeleteRequested(exam)
        sut.confirmDelete()
        advanceUntilIdle()
        assertEquals(ModerationError.PermissionDenied.userMessage, sut.uiState.value.error)

        sut.onErrorDismissed()
        assertNull(sut.uiState.value.error)
    }
}
