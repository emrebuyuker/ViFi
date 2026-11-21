package com.salticusteam.vifi.core.data.moderation

/**
 * The exact document fields one first approval writes (4.2.0, iOS `FirebaseModerationService.commit`), kept
 * Firestore-free so the shape is unit-tested: the backend passes its `FieldValue` sentinels in as [serverTimestamp]
 * and [balanceIncrement]. The shared rules whitelist the wallet and ledger keys, so an extra field would be rejected.
 */
object FirstApprovalWrites {

    /** `submissions/<id>` update: no `publishedExamId` (the server writes it when it publishes). */
    fun submission(commit: ApprovalCommit, serverTimestamp: Any): Map<String, Any> = buildMap {
        put(STATUS, APPROVED)
        put(TIER, commit.tier.raw)
        put(COINS, commit.coins.toLong())
        put(REVIEWED_AT, serverTimestamp)
        put(CONVERSION_PROVIDER, commit.provider.raw)
        commit.publishPlan?.let { put(PUBLISH_PLAN, it) }
    }

    /** `wallets/<owner>` merge (only when crediting): `balance += coins`. */
    fun wallet(balanceIncrement: Any, serverTimestamp: Any): Map<String, Any> =
        mapOf("balance" to balanceIncrement, "updatedAt" to serverTimestamp)

    /** `wallets/<owner>/ledger/<submissionId>` earn entry (only when crediting). */
    fun ledger(commit: ApprovalCommit, serverTimestamp: Any): Map<String, Any> = mapOf(
        "type" to "earn",
        "amount" to commit.coins.toLong(),
        "submissionId" to commit.submissionId,
        "tier" to commit.tier.raw,
        "createdAt" to serverTimestamp,
    )

    const val STATUS = "status"
    const val APPROVED = "approved"
    const val TIER = "tier"
    const val COINS = "coins"
    const val REVIEWED_AT = "reviewedAt"

    /** The submission field the server reads the admin's AI choice from (not `provider`; SOPS-01). */
    const val CONVERSION_PROVIDER = "conversionProvider"
    const val PUBLISH_PLAN = "publishPlan"
}
