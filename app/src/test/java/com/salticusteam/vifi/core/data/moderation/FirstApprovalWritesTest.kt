package com.salticusteam.vifi.core.data.moderation

import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.SubmissionTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The exact first-approval write shape (ADM-01), against iOS `FirebaseModerationService.commit` and the server's
 * `onSubmissionModerated` / `submissionJob` / `publishExamConversion` reads. The sentinels stand in for `FieldValue`.
 */
class FirstApprovalWritesTest {

    private val serverTime = Any()
    private val increment = Any()
    private val plan: Map<String, Any> = mapOf("examId" to "c__2019-vize", "mode" to "create")

    private fun commit(
        coins: Int = 60,
        provider: AIProvider = AIProvider.CLAUDE,
        publishPlan: Map<String, Any>? = plan,
    ) = ApprovalCommit(
        submissionId = "sub-1",
        ownerId = "u1",
        tier = SubmissionTier.NEW_COURSE,
        coins = coins,
        provider = provider,
        publishPlan = publishPlan,
    )

    @Test
    fun `submission update carries status tier coins reviewedAt conversionProvider and publishPlan only`() {
        val fields = FirstApprovalWrites.submission(commit(), serverTime)

        assertEquals(
            mapOf(
                "status" to "approved",
                "tier" to "newCourse",
                "coins" to 60L,
                "reviewedAt" to serverTime,
                "conversionProvider" to "claude",
                "publishPlan" to plan,
            ),
            fields,
        )
        // The server writes the published exam id at the second approval; the old field name `provider` is wrong.
        assertFalse("publishedExamId" in fields)
        assertFalse("provider" in fields)
    }

    @Test
    fun `submission update stores gemini as its raw value`() {
        assertEquals(
            "gemini",
            FirstApprovalWrites.submission(commit(provider = AIProvider.GEMINI), serverTime)["conversionProvider"],
        )
    }

    @Test
    fun `submission update without a plan omits the publishPlan key`() {
        val fields = FirstApprovalWrites.submission(commit(publishPlan = null), serverTime)

        assertFalse("publishPlan" in fields)
        assertEquals(5, fields.size)
    }

    @Test
    fun `ledger entry has the rules' whitelisted earn shape`() {
        assertEquals(
            mapOf(
                "type" to "earn",
                "amount" to 60L,
                "submissionId" to "sub-1",
                "tier" to "newCourse",
                "createdAt" to serverTime,
            ),
            FirstApprovalWrites.ledger(commit(), serverTime),
        )
    }

    @Test
    fun `wallet merge carries only balance and updatedAt`() {
        assertEquals(
            mapOf("balance" to increment, "updatedAt" to serverTime),
            FirstApprovalWrites.wallet(increment, serverTime),
        )
    }
}
