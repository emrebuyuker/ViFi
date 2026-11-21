package com.salticusteam.vifi.core.data.conversion

import com.salticusteam.vifi.core.model.ExamQuestion

/**
 * The write of an admin's draft-question edit (iOS `FirebaseExamConversionService.saveDraftQuestion`): the content
 * fields the Firestore rules allow (`ExamConversionCalls.contentFields` + `needsReview`) plus `editedBy == uid` and
 * `editedAt` = server time. Nothing else may be written, so the allowed key set is the contract ([KEYS]).
 */
object DraftEdit {

    const val NEEDS_REVIEW = "needsReview"
    const val EDITED_BY = "editedBy"
    const val EDITED_AT = "editedAt"

    /** Every key a draft edit writes; the rules reject any other. */
    val KEYS: Set<String> = setOf("text", "speech", "choices", "answer", "solution", NEEDS_REVIEW, EDITED_BY, EDITED_AT)

    /**
     * The update map for [question]. [editedAt] is the server timestamp sentinel (`FieldValue.serverTimestamp()`),
     * passed in so this stays Firebase-free. The edited draft is the admin's judgement, so it no longer asks for a
     * careful look (`needsReview = false`).
     */
    fun fields(question: ExamQuestion, uid: String, editedAt: Any): Map<String, Any> =
        ExamConversionCalls.contentFields(question) +
            mapOf(NEEDS_REVIEW to false, EDITED_BY to uid, EDITED_AT to editedAt)

    /** The draft's document id under `examConversions/<jobId>/questions`: the order, zero-padded to three digits. */
    fun documentId(order: Int): String = "%03d".format(order)
}
