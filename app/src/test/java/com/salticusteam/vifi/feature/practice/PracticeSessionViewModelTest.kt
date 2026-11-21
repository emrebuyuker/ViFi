package com.salticusteam.vifi.feature.practice

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PracticeError
import com.salticusteam.vifi.core.model.PracticeLimits
import com.salticusteam.vifi.core.model.PracticeScope
import com.salticusteam.vifi.feature.practice.PracticeFixture.batchResult
import com.salticusteam.vifi.feature.practice.StubPracticeService.Result
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The practice session's flow (iOS `PracticeSessionViewModelTests`, case for case). */
class PracticeSessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val analytics = FakeAnalyticsTracker()

    private fun makeViewModel(service: StubPracticeService) =
        PracticeSessionViewModel(PracticeFixture.selection, service, analytics)

    private val PracticeSessionViewModel.state get() = uiState.value

    // --- Start and prefetch ---

    @Test
    fun `starting shows the first question, prefetches the next (Premium) and reports the start once`() = runTest {
        val service = StubPracticeService(
            listOf(batchResult(listOf("e1-001"), remaining = null), batchResult(listOf("e1-002"), remaining = null)),
        )
        val sut = makeViewModel(service)

        sut.onStart()
        sut.onStart()
        advanceUntilIdle()

        assertEquals(PracticePhase.Question, sut.state.phase)
        assertEquals("e1-001", sut.state.current?.id)
        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.PracticeStarted(ExamType.FINAL, PracticeScope.TURKEY)),
            analytics.events,
        )
        assertEquals(listOf(1, 1), service.requests.map { it.count })
        assertTrue(service.requests[0].exclude.isEmpty())
        assertEquals(listOf("e1-001"), service.requests[1].exclude)
    }

    @Test
    fun `the next question is the prefetched one and the following request excludes everything seen`() = runTest {
        val service = StubPracticeService(
            listOf(
                batchResult(listOf("e1-001"), remaining = null),
                batchResult(listOf("e1-002"), remaining = null),
                batchResult(listOf("e1-003"), remaining = null),
            ),
        )
        val sut = makeViewModel(service)
        sut.onStart()
        advanceUntilIdle()

        sut.onNext()
        advanceUntilIdle()
        assertEquals("e1-002", sut.state.current?.id)
        assertEquals(3, service.requests.size)

        sut.onNext()
        advanceUntilIdle()
        assertEquals("e1-003", sut.state.current?.id)
        assertEquals(listOf("e1-001", "e1-002"), service.requests[2].exclude)
    }

    @Test
    fun `questions are numbered 1, 2, 3 in the session, and starting over begins at 1 again`() = runTest {
        val service = StubPracticeService(
            listOf(
                batchResult(listOf("e1-004"), remaining = null),
                batchResult(listOf("e2-001"), remaining = null),
                batchResult(emptyList(), remaining = null),
                batchResult(listOf("e1-004"), remaining = null),
            ),
        )
        val sut = makeViewModel(service)
        assertEquals(0, sut.state.questionNumber)

        sut.onStart()
        advanceUntilIdle()
        assertEquals(1, sut.state.questionNumber)
        sut.onNext()
        advanceUntilIdle()
        assertEquals(2, sut.state.questionNumber)
        sut.onNext()
        advanceUntilIdle()
        assertEquals(PracticePhase.Exhausted, sut.state.phase)
        assertEquals(2, sut.state.questionNumber)

        sut.onRestart()
        advanceUntilIdle()
        assertEquals(1, sut.state.questionNumber)
    }

    @Test
    fun `revealing flips the answer, moving on hides it again, and only the first reveal of a question is counted`() =
        runTest {
            val service = StubPracticeService(
                listOf(batchResult(listOf("e1-001"), remaining = null), batchResult(listOf("e1-002"), remaining = null)),
            )
            val sut = makeViewModel(service)
            sut.onStart()
            advanceUntilIdle()

            sut.onToggleAnswer()
            sut.onToggleAnswer()
            sut.onToggleAnswer()
            assertTrue(sut.state.isAnswerRevealed)
            sut.onNext()
            advanceUntilIdle()
            assertFalse(sut.state.isAnswerRevealed)
            sut.onToggleAnswer()

            assertEquals(2, analytics.events.count { it == AnalyticsEvent.PracticeAnswerRevealed })
        }

    // --- Free limit and Premium ---

    @Test
    fun `free users never prefetch, so leaving a session cannot spend a question they did not see`() = runTest {
        val service = StubPracticeService(
            listOf(batchResult(listOf("e1-001"), remaining = 4), batchResult(listOf("e1-002"), remaining = 3)),
        )
        val sut = makeViewModel(service)

        sut.onStart()
        advanceUntilIdle()

        assertEquals(1, service.requests.size)
        // 5 free per day, one on screen: 4 left.
        assertEquals(4, sut.state.remainingToday)

        sut.onNext()
        advanceUntilIdle()
        assertEquals("e1-002", sut.state.current?.id)
        assertEquals(3, sut.state.remainingToday)
        assertEquals(2, service.requests.size)
        assertEquals(listOf("e1-001"), service.requests[1].exclude)
    }

    @Test
    fun `Premium has no counter`() = runTest {
        val sut = makeViewModel(StubPracticeService(fallback = batchResult(listOf("e1-001"), remaining = null)))

        sut.onStart()
        advanceUntilIdle()

        assertNull(sut.state.remainingToday)
    }

    @Test
    fun `when the last free question is out, the next tap lands on the limit state and is counted once`() = runTest {
        val service = StubPracticeService(
            listOf(batchResult(listOf("e1-001"), remaining = 1), batchResult(listOf("e1-002"), remaining = 0)),
        )
        val sut = makeViewModel(service)
        sut.onStart()
        advanceUntilIdle()

        sut.onNext()
        advanceUntilIdle()
        assertEquals("e1-002", sut.state.current?.id)
        assertEquals(PracticePhase.Question, sut.state.phase)

        sut.onNext()
        advanceUntilIdle()
        assertEquals(PracticePhase.LimitReached, sut.state.phase)
        assertNull(sut.state.current)
        sut.onNext()
        advanceUntilIdle()
        assertEquals(1, analytics.events.count { it == AnalyticsEvent.PracticeLimitReached })
        assertEquals("No request once the limit is known", 2, service.requests.size)
    }

    @Test
    fun `a limit reached on the very first call shows the limit state at once`() = runTest {
        val sut = makeViewModel(
            StubPracticeService(fallback = batchResult(emptyList(), remaining = 0, limitReached = true)),
        )

        sut.onStart()
        advanceUntilIdle()

        assertEquals(PracticePhase.LimitReached, sut.state.phase)
        assertTrue(AnalyticsEvent.PracticeLimitReached in analytics.events)
    }

    @Test
    fun `opening the paywall from the limit state is reported and asks the screen to open it`() = runTest {
        val sut = makeViewModel(StubPracticeService())

        sut.events.test {
            sut.onPaywallClicked()
            assertEquals(PracticeSessionEvent.OpenPaywall, awaitItem())
        }

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.PracticePaywallOpened), analytics.events)
    }

    // --- Exhausted pool ---

    @Test
    fun `an exhausted pool is reported, and starting over forgets the seen ids`() = runTest {
        val service = StubPracticeService(
            listOf(
                batchResult(listOf("e1-001"), remaining = null),
                batchResult(emptyList(), remaining = null),
                batchResult(listOf("e1-001"), remaining = null),
            ),
        )
        val sut = makeViewModel(service)
        sut.onStart()
        advanceUntilIdle()

        sut.onNext()
        advanceUntilIdle()
        assertEquals(PracticePhase.Exhausted, sut.state.phase)

        sut.onRestart()
        advanceUntilIdle()
        assertEquals(PracticePhase.Question, sut.state.phase)
        assertEquals("e1-001", sut.state.current?.id)
        assertTrue(service.requests[2].exclude.isEmpty())
    }

    @Test
    fun `a server that returns only seen questions counts as exhausted`() = runTest {
        val service = StubPracticeService(
            listOf(batchResult(listOf("e1-001"), remaining = null), batchResult(listOf("e1-001"), remaining = null)),
        )
        val sut = makeViewModel(service)
        sut.onStart()
        advanceUntilIdle()

        sut.onNext()
        advanceUntilIdle()

        assertEquals(PracticePhase.Exhausted, sut.state.phase)
    }

    @Test
    fun `exclude is capped at the newest 300 seen ids`() = runTest {
        val ids = (1..320).map { "e1-%03d".format(it) }
        val results = ids.map { batchResult(listOf(it), remaining = null) } +
            batchResult(emptyList(), remaining = null)
        val sut = makeViewModel(StubPracticeService(results))
        sut.onStart()
        advanceUntilIdle()

        repeat(319) {
            sut.onNext()
            advanceUntilIdle()
        }

        assertEquals(PracticeLimits.MAX_EXCLUDE, sut.excludedIds.size)
        assertEquals("e1-320", sut.excludedIds.last())
        assertEquals("e1-021", sut.excludedIds.first())
    }

    // --- Errors ---

    @Test
    fun `a failed first load shows the message, and retrying recovers`() = runTest {
        val service = StubPracticeService(
            listOf(Result.Failure(PracticeError.Offline), batchResult(listOf("e1-001"), remaining = null)),
        )
        val sut = makeViewModel(service)

        sut.onStart()
        advanceUntilIdle()
        assertEquals(PracticePhase.Failed(PracticeError.Offline.userMessage), sut.state.phase)

        sut.onNext()
        advanceUntilIdle()
        assertEquals(PracticePhase.Question, sut.state.phase)
        assertEquals("e1-001", sut.state.current?.id)
    }

    @Test
    fun `a failed prefetch stays silent until the user asks for the next question, then the call is retried`() =
        runTest {
            val service = StubPracticeService(
                listOf(
                    batchResult(listOf("e1-001"), remaining = null),
                    Result.Failure(PracticeError.Offline),
                    batchResult(listOf("e1-002"), remaining = null),
                ),
            )
            val sut = makeViewModel(service)
            sut.onStart()
            advanceUntilIdle()

            assertEquals(PracticePhase.Question, sut.state.phase)
            assertEquals("e1-001", sut.state.current?.id)

            sut.onNext()
            advanceUntilIdle()
            assertEquals("e1-002", sut.state.current?.id)
            assertEquals(PracticePhase.Question, sut.state.phase)
        }

    // --- State the screen draws ---

    @Test
    fun `the state starts loading with the selection's pool size and takes the server's larger one`() = runTest {
        val service = StubPracticeService(
            listOf(Result.Batch(PracticeFixture.batch(listOf("e1-001"), remaining = 4, poolSize = 40))),
        )
        val sut = makeViewModel(service)

        assertEquals(PracticePhase.Loading, sut.state.phase)
        assertEquals(PracticeFixture.selection.poolCount, sut.state.poolSize)

        sut.onStart()
        advanceUntilIdle()

        assertEquals(40, sut.state.poolSize)
    }
}
