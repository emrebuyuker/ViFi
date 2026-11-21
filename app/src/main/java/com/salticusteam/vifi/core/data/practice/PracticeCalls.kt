package com.salticusteam.vifi.core.data.practice

import com.salticusteam.vifi.core.model.PracticeLimits
import com.salticusteam.vifi.core.model.PracticeRequest
import com.salticusteam.vifi.core.model.PracticeScope
import com.salticusteam.vifi.core.model.SubscriptionProof

/**
 * The pure half of the practice callable (iOS `FirebasePracticeService.payload`): its name and request body. Contract:
 * `nextPracticeQuestions {subjectId, examType, scope, courseId? (course), universityId? (university), exclude ≤ 300,
 * count 1…10, subscriptionProof?}` in the shared schema.js (europe-west1).
 */
object PracticeCalls {

    const val NEXT_PRACTICE_QUESTIONS = "nextPracticeQuestions"

    /** The request body: the newest [PracticeLimits.MAX_EXCLUDE] seen ids, the count clamped, the proof when known. */
    fun payload(request: PracticeRequest, proof: SubscriptionProof?): Map<String, Any> {
        val selection = request.selection
        val body = mutableMapOf<String, Any>(
            "subjectId" to selection.subjectId,
            "examType" to selection.examType.raw,
            "scope" to selection.scope.raw,
            "exclude" to request.exclude.takeLast(PracticeLimits.MAX_EXCLUDE),
            "count" to request.count.coerceIn(1, PracticeLimits.MAX_BATCH),
        )
        when (selection.scope) {
            PracticeScope.COURSE -> body["courseId"] = selection.courseId
            PracticeScope.UNIVERSITY -> selection.universityId?.let { body["universityId"] = it }
            PracticeScope.TURKEY -> Unit
        }
        if (proof != null && proof.purchaseToken.isNotEmpty()) body["subscriptionProof"] = proof.payload()
        return body
    }
}
