package com.salticusteam.vifi.core.model

// Added in 2.5 — the result of approving a submission.
data class ApprovalResult(
    /** The tier the submission was judged at. */
    val tier: SubmissionTier,
    /** The coins granted. */
    val coins: Int,
    /** The id of the exam it published; `null` when it duplicated an existing exam. */
    val publishedExamId: String?,
)
