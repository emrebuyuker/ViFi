package com.salticusteam.vifi.core.data.billing

import com.salticusteam.vifi.core.data.billing.FakePlayBillingClient.Companion.examUnlock
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.model.PurchaseResult
import com.salticusteam.vifi.core.model.StoreProductIds
import com.salticusteam.vifi.core.model.SubscriptionError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayBillingExamPurchaseServiceTest {

    private val calls = mutableListOf<String>()
    private val billing = FakePlayBillingClient(calls)
    private val backend = FakeExamPurchaseBackend(calls)
    private val pending = FakePendingExamUnlockStore()
    private val auth = FakeAuthService()
    private val sut = PlayBillingExamPurchaseService(billing, backend, pending, auth)

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
        assertEquals("exam-1", request.obfuscatedProfileId)
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
    fun `purchaseUnlock when consuming fails still succeeds once recorded`() = runTest {
        billing.sheetAnswer = paid()
        billing.consumeFailure = PlayBillingResponse.SERVICE_DISCONNECTED

        assertEquals(PurchaseResult.SUCCESS, sut.purchaseUnlock("exam-1"))
        assertEquals(listOf("launch", "record:t1:exam_unlock:exam-1", "consume:t1"), calls)
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
        backend.failure = SubscriptionError.StoreUnavailable

        assertEquals(SubscriptionError.StoreUnavailable, failureOf { sut.purchaseUnlock("exam-1") })
        assertTrue(billing.launched.isEmpty())
    }

    @Test
    fun `purchaseUnlock while an earlier payment is pending reports pending`() = runTest {
        billing.purchases[PlayProductType.ONE_TIME] = listOf(examUnlock("old", "exam-0", PlayPurchaseState.PENDING))

        assertEquals(PurchaseResult.PENDING, sut.purchaseUnlock("exam-1"))
        assertTrue(billing.launched.isEmpty())
    }

    @Test
    fun `purchaseUnlock when the exam id is too long to bind fails before paying`() = runTest {
        val longId = "e".repeat(PlayPurchaseRequest.MAX_OBFUSCATED_ID_LENGTH + 1)

        assertEquals(SubscriptionError.Unknown, failureOf { sut.purchaseUnlock(longId) })
        assertTrue(billing.launched.isEmpty())
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
    fun `recoverPendingUnlock records then consumes the exam the purchase names`() = runTest {
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
    fun `recoverPendingUnlock falls back to the remembered exam`() = runTest {
        billing.purchases[PlayProductType.ONE_TIME] = listOf(examUnlock("t1", examId = null))
        pending.stored = "exam-2"

        assertEquals("exam-2", sut.recoverPendingUnlock())
        assertEquals(listOf("record:t1:exam_unlock:exam-2", "consume:t1"), calls)
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

    // --- obfuscatedAccountId ---

    @Test
    fun `obfuscatedAccountId is a 64-character SHA-256 hex digest`() {
        val id = PlayBillingExamPurchaseService.obfuscatedAccountId("abc")

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", id)
        assertEquals(PlayPurchaseRequest.MAX_OBFUSCATED_ID_LENGTH, id.length)
    }
}
