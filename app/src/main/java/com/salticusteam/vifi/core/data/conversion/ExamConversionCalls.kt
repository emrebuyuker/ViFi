package com.salticusteam.vifi.core.data.conversion

import com.salticusteam.vifi.core.model.AIMonthlyUsage
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.BulkConversionEstimate
import com.salticusteam.vifi.core.model.BulkConversionScope
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.PublishedExamResult
import com.salticusteam.vifi.core.util.RawValues

/**
 * The pure half of the conversion admin service (iOS `ExamConversionCalls`): callable names, request payloads,
 * response parsing and the draft-edit field map. Mirrors `CALLABLES` in the shared schema.js (europe-west1, admin claim).
 */
object ExamConversionCalls {

    object Name {
        const val REQUEST_CONVERSION = "requestExamConversion"
        const val REQUEST_BULK = "requestBulkConversion"
        const val REGENERATE = "regenerateExamConversion"
        const val REGENERATE_DRAFT_QUESTION = "regenerateDraftQuestion"
        const val PUBLISH = "publishExamConversion"
        const val DISCARD = "discardExamConversion"
        const val UPDATE_PUBLISHED_QUESTION = "updatePublishedQuestion"
        const val REGENERATE_PUBLISHED_QUESTION = "regeneratePublishedQuestion"
    }

    // Requests

    fun requestConversion(examId: String, provider: AIProvider): Map<String, Any> =
        mapOf("examId" to examId, "provider" to provider.raw)

    fun requestBulk(scope: BulkConversionScope, provider: AIProvider, dryRun: Boolean): Map<String, Any> {
        val payload = mutableMapOf<String, Any>("provider" to provider.raw, "dryRun" to dryRun)
        when (scope) {
            is BulkConversionScope.Course -> payload["courseId"] = scope.id
            is BulkConversionScope.Department -> payload["departmentId"] = scope.id
        }
        return payload
    }

    fun regenerate(jobId: String, provider: AIProvider): Map<String, Any> = mapOf("jobId" to jobId, "provider" to provider.raw)

    fun regenerateDraftQuestion(jobId: String, order: Int, provider: AIProvider): Map<String, Any> =
        mapOf("jobId" to jobId, "order" to order, "provider" to provider.raw)

    /** A blank subject field or exam title is left out, so the server falls back to its own suggestion. */
    fun publish(jobId: String, subjectId: String?, newSubjectName: String?, examTitle: String?): Map<String, Any> {
        val payload = mutableMapOf<String, Any>("jobId" to jobId)
        trimmed(subjectId)?.let { payload["subjectId"] = it }
        trimmed(newSubjectName)?.let { payload["newSubjectName"] = it }
        trimmed(examTitle)?.let { payload["examTitle"] = it }
        return payload
    }

    fun discard(jobId: String): Map<String, Any> = mapOf("jobId" to jobId)

    fun updatePublishedQuestion(question: ExamQuestion): Map<String, Any> =
        contentFields(question) + ("questionId" to question.id)

    fun regeneratePublishedQuestion(questionId: String, provider: AIProvider): Map<String, Any> =
        mapOf("questionId" to questionId, "provider" to provider.raw)

    /**
     * The editable content of a question (`text`, `speech`, `choices`, `answer`, `solution`): the only fields the
     * draft-edit rules and `updatePublishedQuestion` accept.
     */
    fun contentFields(question: ExamQuestion): Map<String, Any> {
        val answer = mutableMapOf<String, Any>("text" to question.answer.text)
        question.answer.choice?.let { answer["choice"] = it }
        question.answer.speech?.let { answer["speech"] = it }
        return mapOf(
            "text" to question.text,
            "speech" to question.speech,
            "choices" to question.choices.map { mapOf("label" to it.label, "text" to it.text) },
            "answer" to answer,
            "solution" to question.solution,
        )
    }

    // Responses

    fun status(data: Any?): ConversionStatus? = ConversionStatus.fromRaw((data as? Map<*, *>)?.get("status") as? String)

    fun jobId(data: Any?): String? = (data as? Map<*, *>)?.get("jobId") as? String

    fun bulkEstimate(data: Any?): BulkConversionEstimate {
        val map = data as? Map<*, *>
        return BulkConversionEstimate(
            examCount = ((map?.get("examCount") as? Number)?.toInt() ?: 0).coerceAtLeast(0),
            estimatedCostUsd = (RawValues.double(map?.get("estimatedCostUsd")) ?: 0.0).coerceAtLeast(0.0),
            jobIds = (map?.get("jobIds") as? List<*>).orEmpty().filterIsInstance<String>(),
        )
    }

    fun publishResult(data: Any?): PublishedExamResult? {
        val map = data as? Map<*, *> ?: return null
        val examId = (map["examId"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        return PublishedExamResult(examId, ((map["questionCount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0))
    }

    /** `aiUsage/<YYYY-MM>`: the month's spend, `costUsd` (or `totalCostUsd`); a missing document is zero spend. */
    fun monthlyUsage(month: String, data: Map<String, Any?>?): AIMonthlyUsage {
        val cost = RawValues.double(data?.get("costUsd")) ?: RawValues.double(data?.get("totalCostUsd")) ?: 0.0
        return AIMonthlyUsage(month, cost.coerceAtLeast(0.0))
    }

    private fun trimmed(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}
