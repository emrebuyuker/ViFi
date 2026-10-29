package com.salticusteam.vifi.core.data.billing

import com.salticusteam.vifi.core.data.billing.FakePlayBillingClient.Companion.examUnlock
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.StoreProductIds
import com.salticusteam.vifi.core.model.SubscriptionError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayBillingExamPurchaseServiceTest {

    private val calls = mutableListOf<String>()
    private val billing = FakePlayBillingClient(calls)
    private val backend = FakeExamPurchaseBackend(calls)
    private val pending = FakePendingExamUnlockStore()
    private val auth = FakeAuthService()

    // The background consume runs as soon as the purchase lets go of the lock, so tests read its effect directly.
    private val sut = PlayBillingExamPurchaseService(
        billing, backend, pending, auth, CoroutineScope(UnconfinedTestDispatcher()),
    )

    private fun paid(token: String = "t1", examId: String? = "exam-1") =
        PlayPurchaseUpdate(PlayBillingResponse.OK, listOf(examUnlock(token, examId)))

    private inline fun failureOf(block: () -> Unit): Throwable? = runCatching(block).exceptionOrNull()

    // --- purchaseUnlock ---

    @Test
    fun `purchaseUnlock binds the exam and user, records on the server, then consumes`() = runTest {
        billing.sheetAnswer = paid()

        assertEquals(PurchaseResult.SUCCESS, sut.purchaseUnlock("exam-1"))

        val request = billing.launched.single()
        assertEquals(StoreProductIds.EXAM_UNLOCK, request.productId)
        assertEquals(PlayProductType.ONE_TIME, request.type)
        assertEquals("token-exam", request.offerToken)
        assertEquals(PlayBillingExamPurchaseService.examBinding("exam-1"), request.obfuscatedProfileId)
        val uid = checkNotNull(auth.currentUser).id
        assertEquals(PlayBillingExamPurchaseService.obfuscatedAccountId(uid), request.obfuscatedAccountId)
        assertEquals(listOf("launch", "record:t1:exam_unlock:exam-1", "consume:t1"), calls)
        assertNull(pending.stored)
    }

    @Test
    fun `purchaseUnlock when the server fails keeps the purchase unconsumed and the exam remembered`() = runTest {
        billing.sheetAnswer = paid()
        backend.failure = SubscriptionError.FailedVerification

        assertEquals(SubscriptionError.FailedVerification, failureOf { sut.purchaseUnlock("exam-1") })
        assertEquals(listOf("launch", "record:t1:exam_unlock:exam-1"), calls)
        assertEquals("exam-1", pending.stored)
    }

    @Test
    fun `purchaseUnlock when consuming fails still succeeds and keeps the exam for the next recovery`() = runTest {
        billing.sheetAnswer = paid()
        billing.consumeFailure = PlayBillingResponse.SERVICE_DISCONNECTED

        assertEquals(PurchaseResult.SUCCESS, sut.purchaseUnlock("exam-1"))
        assertEquals(listOf("launch", "record:t1:exam_unlock:exam-1", "consume:t1"), calls)
        assertEquals("exam-1", pending.stored)
    }

    @Test
    fun `purchaseUnlock opens the exam once recorded and consumes behind it`() = runTest {
        val later = PlayBillingExamPurchaseService(
            billing, backend, pending, auth, CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        billing.sheetAnswer = paid()

        assertEquals(PurchaseResult.SUCCESS, later.purchaseUnlock("exam-1"))
        assertEquals(listOf("launch", "record:t1:exam_unlock:exam-1"), calls)
        assertEquals("exam-1", pending.stored)

        advanceUntilIdle()
        assertEquals(listOf("launch", "record:t1:exam_unlock:exam-1", "consume:t1"), calls)
        assertNull(pending.stored)
    }

    @Test
    fun `purchaseUnlock when the payment is pending keeps the exam and records nothing`() = runTest {
        billing.sheetAnswer = PlayPurchaseUpdate(
            PlayBillingResponse.OK,
            listOf(examUnlock("t1", "exam-1", PlayPurchaseState.PENDING)),
        )

        assertEquals(PurchaseResult.PENDING, sut.purchaseUnlock("exam-1"))
        assertEquals(listOf("launch"), calls)
        assertEquals("exam-1", pending.stored)
    }

    @Test
    fun `purchaseUnlock when the user cancels forgets the exam`() = runTest {
        billing.sheetAnswer = PlayPurchaseUpdate(PlayBillingResponse.USER_CANCELED)

        assertEquals(PurchaseResult.CANCELLED, sut.purchaseUnlock("exam-1"))
        assertNull(pending.stored)
    }

    @Test
    fun `purchaseUnlock when Play fails maps the error and forgets the exam`() = runTest {
        billing.sheetAnswer = PlayPurchaseUpdate(PlayBillingResponse.NETWORK_ERROR)

        assertEquals(SubscriptionError.StoreUnavailable, failureOf { sut.purchaseUnlock("exam-1") })
        assertNull(pending.stored)
    }

    @Test
    fun `purchaseUnlock when Play does not sell the unlock throws productUnavailable`() = runTest {
        billing.offers = emptyMap()

        assertEquals(SubscriptionError.ProductUnavailable, failureOf { sut.purchaseUnlock("exam-1") })
        assertTrue(billing.launched.isEmpty())
        assertNull(pending.stored)
    }

    @Test
    fun `purchaseUnlock first finishes an earlier unconsumed unlock of the same exam without paying again`() = runTest {
        billing.purchases[PlayProductType.ONE_TIME] = listOf(examUnlock("old", "exam-1"))

        assertEquals(PurchaseResult.SUCCESS, sut.purchaseUnlock("exam-1"))
        assertEquals(listOf("record:old:exam_unlock:exam-1", "consume:old"), calls)
        assertTrue(billing.launched.isEmpty())
    }

    @Test
    fun `purchaseUnlock first finishes another exam's unlock, then buys`() = runTest {
        billing.purchases[PlayProductType.ONE_TIME] = listOf(examUnlock("old", "exam-0"))
        pending.stored = "exam-0"
        billing.sheetAnswer = paid()

        assertEquals(PurchaseResult.SUCCESS, sut.purchaseUnlock("exam-1"))
        assertEquals(
            listOf(
                "record:old:exam_unlock:exam-0",
                "consume:old",
                "launch",
                "record:t1:exam_unlock:exam-1",
                "consume:t1",
            ),
            calls,
        )
    }

    @Test
    fun `purchaseUnlock when an earlier unlock cannot be recorded does not buy`() = runTest {
        billing.purchases[PlayProductType.ONE_TIME] = listOf(examUnlock("old", "exam-0"))
        pending.stored = "exam-0"
        backend.failure = SubscriptionError.StoreUnavailable

        assertEquals(SubscriptionError.StoreUnavailable, failureOf { sut.purchaseUnlock("exam-1") })
        assertTrue(billing.launched.isEmpty())
    }

    @Test
    fun `purchaseUnlock while a recorded unlock cannot be consumed does not buy`() = runTest {
        billing.purchases[PlayProductType.ONE_TIME] = listOf(examUnlock("old", "exam-0"))
        pending.stored = "exam-0"
        billing.consumeFailure = PlayBillingResponse.ERROR

        assertEquals(SubscriptionError.StoreUnavailable, failureOf { sut.purchaseUnlock("exam-1") })
        assertEquals(listOf("record:old:exam_unlock:exam-0", "consume:old"), calls)
        assertTrue(billing.launched.isEmpty())
        assertEquals("exam-0", pending.stored)
    }

    @Test
    fun `purchaseUnlock while an earlier payment is pending reports pending`() = runTest {
        billing.purchases[PlayProductType.ONE_TIME] = listOf(examUnlock("old", "exam-0", PlayPurchaseState.PENDING))

        assertEquals(PurchaseResult.PENDING, sut.purchaseUnlock("exam-1"))
        assertTrue(billing.launched.isEmpty())
    }

    @Test
    fun `purchaseUnlock binds a catalog-path exam id longer than Play allows through its digest`() = runTest {
        val longId = "sakarya-universitesi__muhendislik-fakultesi__insaat-muhendisligi__" +
            "betonarme-1__2009-yaz-okulu-vize"
        billing.sheetAnswer = paid(examId = longId)

        assertEquals(PurchaseResult.SUCCESS, sut.purchaseUnlock(longId))
        val binding = checkNotNull(billing.launched.single().obfuscatedProfileId)
        assertEquals(PlayPurchaseRequest.MAX_OBFUSCATED_ID_LENGTH, binding.length)
        assertEquals(listOf("launch", "record:t1:exam_unlock:$longId", "consume:t1"), calls)
    }

    @Test
    fun `purchaseUnlock when signed out fails before paying`() = runTest {
        auth.signOut()

        assertEquals(SubscriptionError.Unknown, failureOf { sut.purchaseUnlock("exam-1") })
        assertTrue(billing.launched.isEmpty())
        assertNull(pending.stored)
    }

    // --- recoverPendingUnlock ---

    @Test
    fun `recoverPendingUnlock records then consumes the remembered exam the purchase is bound to`() = runTest {
        billing.purchases[PlayProductType.ONE_TIME] = listOf(examUnlock("t1", "exam-1"))
        pending.stored = "exam-1"

        assertEquals("exam-1", sut.recoverPendingUnlock())
        assertEquals(listOf("record:t1:exam_unlock:exam-1", "consume:t1"), calls)
        assertNull(pending.stored)
        // The same purchase is never recorded twice.
        assertNull(sut.recoverPendingUnlock())
        assertEquals(2, calls.size)
    }

    @Test
    fun `recoverPendingUnlock leaves a purchase bound to an exam this device does not remember`() = runTest {
        billing.purchases[PlayProductType.ONE_TIME] = listOf(examUnlock("t1", "exam-1"))
        pending.stored = "exam-2"

        assertNull(sut.recoverPendingUnlock())
        assertTrue(calls.isEmpty())
        assertEquals("exam-2", pending.stored)
    }

    @Test
    fun `recoverPendingUnlock leaves a purchase bound to no exam`() = runTest {
        billing.purchases[PlayProductType.ONE_TIME] = listOf(examUnlock("t1", examId = null))
        pending.stored = "exam-2"

        assertNull(sut.recoverPendingUnlock())
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `recoverPendingUnlock when the server fails keeps everything for the next launch`() = runTest {
        billing.purchases[PlayProductType.ONE_TIME] = listOf(examUnlock("t1", "exam-1"))
        pending.stored = "exam-1"
        backend.failure = SubscriptionError.StoreUnavailable

        assertNull(sut.recoverPendingUnlock())
        assertEquals(listOf("record:t1:exam_unlock:exam-1"), calls)
        assertEquals("exam-1", pending.stored)

        backend.failure = null
        assertEquals("exam-1", sut.recoverPendingUnlock())
    }

    @Test
    fun `recoverPendingUnlock when consuming fails keeps the exam and retries next time`() = runTest {
        billing.purchases[PlayProductType.ONE_TIME] = listOf(examUnlock("t1", "exam-1"))
        pending.stored = "exam-1"
        billing.consumeFailure = PlayBillingResponse.ERROR

        assertEquals("exam-1", sut.recoverPendingUnlock())
        assertEquals("exam-1", pending.stored)

        billing.consumeFailure = null
        assertEquals("exam-1", sut.recoverPendingUnlock())
        assertEquals(
            listOf("record:t1:exam_unlock:exam-1", "consume:t1", "record:t1:exam_unlock:exam-1", "consume:t1"),
            calls,
        )
        assertNull(pending.stored)
    }

    @Test
    fun `recoverPendingUnlock counts a purchase Play no longer owns as consumed`() = runTest {
        billing.purchases[PlayProductType.ONE_TIME] = listOf(examUnlock("t1", "exam-1"))
        pending.stored = "exam-1"
        billing.consumeFailure = PlayBillingResponse.ITEM_NOT_OWNED

        assertEquals("exam-1", sut.recoverPendingUnlock())
        assertNull(pending.stored)
    }

    @Test
    fun `recoverPendingUnlock leaves a pending payment alone`() = runTest {
        billing.purchases[PlayProductType.ONE_TIME] = listOf(examUnlock("t1", "exam-1", PlayPurchaseState.PENDING))
        pending.stored = "exam-1"

        assertNull(sut.recoverPendingUnlock())
        assertTrue(calls.isEmpty())
        assertEquals("exam-1", pending.stored)
    }

    @Test
    fun `recoverPendingUnlock with nothing paid forgets a stale exam`() = runTest {
        pending.stored = "exam-1"

        assertNull(sut.recoverPendingUnlock())
        assertNull(pending.stored)
    }

    @Test
    fun `recoverPendingUnlock when signed out leaves the purchase for the next sign-in`() = runTest {
        billing.purchases[PlayProductType.ONE_TIME] = listOf(examUnlock("t1", "exam-1"))
        auth.signOut()

        assertNull(sut.recoverPendingUnlock())
        assertTrue(calls.isEmpty())
        assertEquals(0, billing.purchaseQueries)
    }

    @Test
    fun `recoverPendingUnlock when Play is out of reach returns null`() = runTest {
        billing.queryFailure = PlayBillingResponse.SERVICE_UNAVAILABLE
        pending.stored = "exam-1"

        assertNull(sut.recoverPendingUnlock())
        assertEquals("exam-1", pending.stored)
    }

    // --- obfuscated ids ---

    @Test
    fun `obfuscatedAccountId is a 64-character SHA-256 hex digest`() {
        val id = PlayBillingExamPurchaseService.obfuscatedAccountId("abc")

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", id)
        assertEquals(PlayPurchaseRequest.MAX_OBFUSCATED_ID_LENGTH, id.length)
    }

    @Test
    fun `examBinding is the SHA-256 hex digest the server checks`() {
        // The same digest the server's examBindingFor computes (googlePlay.test.mjs).
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            PlayBillingExamPurchaseService.examBinding("abc"),
        )
    }
}
