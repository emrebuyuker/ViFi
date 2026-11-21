package com.salticusteam.vifi.core.data.conversion

import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.ConversionSource
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ConversionUsage
import com.salticusteam.vifi.core.model.ExamConversion
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.SubmissionFileRef
import com.salticusteam.vifi.core.util.ExamListTitle
import com.salticusteam.vifi.core.util.ExamTitleParser
import com.salticusteam.vifi.core.util.RawValues
import java.time.Instant

/**
 * Turns a raw `examConversions/<jobId>` document into an [ExamConversion] (iOS `ExamConversionParser`; admin screens).
 *
 * Pure and Firestore-free: callers convert `Timestamp` values to [Instant] first. Shape (server-written):
 * `{ source: {type: legacy|submission, examId?, submissionId?}, kind: JPG|PDF, status, model: {provider, id}, attempts,
 * error?, usage: {inputTokens, outputTokens, costUsd}, placement: {names, ids, subjectId?, subjectName?, originalTitle?,
 * suggestedTitle?}, files: [{storagePath, order}], createdAt, readyAt? }`. A job without a valid source, kind or status
 * is skipped.
 */
object ExamConversionParser {

    fun conversion(id: String, data: Map<String, Any?>): ExamConversion? {
        val source = source(data["source"]) ?: return null
        val kind = ExamKind.fromRaw(data["kind"] as? String) ?: return null
        val status = ConversionStatus.fromRaw(data["status"] as? String) ?: return null
        val model = data["model"] as? Map<*, *>
        val placement = data["placement"] as? Map<*, *>
        return ExamConversion(
            id = id,
            source = source,
            kind = kind,
            status = status,
            provider = AIProvider.fromRaw(model?.get("provider") as? String),
            attempts = (RawValues.int(data["attempts"]) ?: 0).coerceAtLeast(0),
            error = (data["error"] as? String)?.takeIf { it.isNotEmpty() },
            usage = usage(data["usage"]),
            placementNames = (placement?.get("names") as? List<*>).orEmpty().filterIsInstance<String>(),
            createdAt = data["createdAt"] as? Instant,
            readyAt = data["readyAt"] as? Instant,
            files = files(data["files"]),
            suggestedSubjectId = RawValues.nonEmpty(placement?.get("subjectId")),
            suggestedSubjectName = RawValues.nonEmpty(placement?.get("subjectName")),
            originalTitle = originalTitle(placement),
            suggestedTitle = suggestedTitle(placement),
        )
    }

    /** `placement.originalTitle`, else the exam name of `placement.names` (jobs created before K28). */
    private fun originalTitle(placement: Map<*, *>?): String? =
        RawValues.nonEmpty(placement?.get("originalTitle"))
            ?: RawValues.nonEmpty((placement?.get("names") as? List<*>)?.lastOrNull())

    /**
     * `placement.suggestedTitle` when it is a list title (`null` = none); a job without the field (before K28) gets the
     * suggestion computed from its original title, as the server does at publish.
     */
    private fun suggestedTitle(placement: Map<*, *>?): String? {
        if (placement == null) return null
        if (placement.containsKey("suggestedTitle")) {
            return RawValues.nonEmpty(placement["suggestedTitle"])?.let(ExamListTitle::parse)?.title
        }
        return originalTitle(placement)?.let(ExamTitleParser::canonicalTitle)?.title
    }

    private fun files(value: Any?): List<SubmissionFileRef> = RawValues.maps(value).mapNotNull { entry ->
        val path = RawValues.nonEmpty(entry["storagePath"]) ?: return@mapNotNull null
        val order = RawValues.int(entry["order"]) ?: return@mapNotNull null
        SubmissionFileRef(storagePath = path, order = order)
    }

    private fun source(value: Any?): ConversionSource? {
        val map = value as? Map<*, *> ?: return null
        return when (map["type"]) {
            "legacy" -> (map["examId"] as? String)?.takeIf { it.isNotEmpty() }?.let { ConversionSource.Legacy(it) }
            "submission" -> (map["submissionId"] as? String)?.takeIf { it.isNotEmpty() }?.let { ConversionSource.Submission(it) }
            else -> null
        }
    }

    /** `costUsd` may arrive as a whole number or a double (SOPS-01), so any number is read. */
    private fun usage(value: Any?): ConversionUsage? {
        val map = value as? Map<*, *> ?: return null
        return ConversionUsage(
            inputTokens = (RawValues.int(map["inputTokens"]) ?: 0).coerceAtLeast(0),
            outputTokens = (RawValues.int(map["outputTokens"]) ?: 0).coerceAtLeast(0),
            costUsd = (RawValues.double(map["costUsd"]) ?: 0.0).coerceAtLeast(0.0),
        )
    }
}
