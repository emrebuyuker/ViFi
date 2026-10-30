package com.salticusteam.vifi.feature.inbox

import com.salticusteam.vifi.core.data.archive.ExamAccessResolver
import com.salticusteam.vifi.core.data.billing.SubscriptionStore
import com.salticusteam.vifi.core.data.coins.ExamUnlocksStore
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubscriptionService
import com.salticusteam.vifi.core.data.repository.fake.FakeUnlocksReader
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.InboxError
import com.salticusteam.vifi.core.model.NotificationExamTarget
import com.salticusteam.vifi.feature.browse.ExamUnlockEvent
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `InboxExamGateTests` (8). */
@OptIn(ExperimentalCoroutinesApi::class)
class InboxExamGateTest {

    private val archive = FakeArchiveRepository()
    private val lesson = ArchivePath.ROOT.appending("itu", "İTÜ").appending("muh", "Müh").appending("blg", "Blg")
        .appending("blg101", "BLG 101")

    private fun target(examId: String, kind: ExamKind = ExamKind.PDF) =
        NotificationExamTarget(examId, lesson.appending(examId, examId), kind)

    private fun TestScope.sut(
        repository: ArchiveRepository = archive,
        unlocked: Set<String> = emptySet(),
        subscribed: Boolean = false,
    ): InboxExamGate {
        val scope = backgroundScope
        val unlocks = ExamUnlocksStore(FakeUnlocksReader(unlocked), SessionStore(FakeAuthService(), scope), scope)
        val subscription = SubscriptionStore(FakeSubscriptionService(subscribed, latency = Duration.ZERO), scope)
        return InboxExamGate(ExamAccessResolver(repository), unlocks, subscription)
    }

    @Test
    fun `free, unlocked and subscribed exams open straight away`() = runTest(UnconfinedTestDispatcher()) {
        assertTrue(sut().check(target("2020-final")))
        assertTrue(sut(unlocked = setOf("2023-vize")).check(target("2023-vize")))
        assertTrue(sut(subscribed = true).check(target("2022-final", ExamKind.IMAGES)))
    }

    @Test
    fun `a locked exam offers the unlock sheet for it`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        assertFalse(sut.check(target("2023-vize")))

        assertEquals(lesson.appending("2023-vize", "2023 Vize"), sut.state.value.lockedExam?.path)
        assertNull(sut.state.value.errorMessage)
        assertNull(sut.state.value.checkingExamId)
    }

    @Test
    fun `once unlocked the exam opens, and only once`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        val target = target("2023-vize")
        sut.check(target)
        val exam = checkNotNull(sut.state.value.lockedExam)

        assertEquals(InboxExamFollowUp.Open(target), sut.followUp(ExamUnlockEvent.Unlocked(exam)))
        assertNull(sut.followUp(ExamUnlockEvent.Unlocked(exam)))
        assertNull(sut.state.value.lockedExam)
    }

    @Test
    fun `earning coins or going Premium closes the sheet and leads there`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        sut.check(target("2023-vize"))
        assertEquals(InboxExamFollowUp.AddExam(lesson), sut.followUp(ExamUnlockEvent.EarnCoins(lesson)))
        assertNull(sut.state.value.lockedExam)

        sut.check(target("2022-final", ExamKind.IMAGES))
        assertEquals(InboxExamFollowUp.Paywall, sut.followUp(ExamUnlockEvent.GoPremium))
        assertNull(sut.state.value.lockedExam)
    }

    @Test
    fun `closing the sheet without unlocking leads nowhere`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        sut.check(target("2023-vize"))
        val exam = checkNotNull(sut.state.value.lockedExam)

        sut.onSheetDismissed()

        assertNull(sut.state.value.lockedExam)
        assertNull(sut.followUp(ExamUnlockEvent.Unlocked(exam)))
    }

    @Test
    fun `a missing exam says it is no longer in the archive and shows no sheet`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        assertFalse(sut.check(target("silinmis")))

        assertEquals("Bu sınav artık arşivde bulunmuyor.", sut.state.value.errorMessage)
        assertEquals(InboxError.ExamMissing.userMessage, sut.state.value.errorMessage)
        assertNull(sut.state.value.lockedExam)
        sut.onErrorShown()
        assertNull(sut.state.value.errorMessage)
    }

    @Test
    fun `a failed read shows the archive's message`() = runTest(UnconfinedTestDispatcher()) {
        archive.error = ArchiveError.Offline
        val sut = sut()

        assertFalse(sut.check(target("2023-vize")))

        assertEquals(ArchiveError.Offline.userMessage, sut.state.value.errorMessage)
    }

    @Test
    fun `a second tap while checking is ignored and only one request is made`() = runTest(UnconfinedTestDispatcher()) {
        val answer = CompletableDeferred<Unit>()
        var requests = 0
        val slow = object : ArchiveRepository {
            override suspend fun items(path: ArchivePath): List<ArchiveItem> {
                requests++
                answer.await()
                return archive.items(path)
            }

            override suspend fun document(path: ArchivePath): ExamDocument = archive.document(path)
        }
        val sut = sut(repository = slow)

        val first = async { sut.check(target("2020-final")) }
        assertEquals("2020-final", sut.state.value.checkingExamId)
        assertFalse(sut.check(target("2021-vize")))
        answer.complete(Unit)

        assertTrue(first.await())
        assertEquals(1, requests)
        assertNull(sut.state.value.checkingExamId)
    }
}
