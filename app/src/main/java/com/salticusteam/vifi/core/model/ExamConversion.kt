package com.salticusteam.vifi.core.model

import java.time.Instant
import java.time.ZoneOffset

// An AI conversion job (admin only, iOS `ExamConversion` + `ExamConversionAdmin`): one exam's pages → draft questions →
// admin review → publish. The read side of `examConversions/<jobId>` and the admin callables in the shared schema.js.

// Added in 4.2.0 — the status of a conversion job; [raw] is the backend value.
enum class ConversionStatus(val raw: String) {
    QUEUED("queued"),
    PROCESSING("processing"),
    READY("ready"),
    FAILED("failed"),
    PUBLISHED("published"),
    DISCARDED("discarded");

    /** `published` and `discarded` never change again. */
    val isTerminal: Boolean get() = this == PUBLISHED || this == DISCARDED

    companion object {
        fun fromRaw(raw: String?): ConversionStatus? = entries.firstOrNull { it.raw == raw }
    }
}

// Added in 4.2.0 — where a job's files came from: an archive exam converted by an admin, or an approved submission.
sealed interface ConversionSource {
    /** The job's document id: `exam-<examId>` / `sub-<submissionId>` (`conversionJobId` on the server). */
    val jobId: String

    data class Legacy(val examId: String) : ConversionSource {
        override val jobId: String get() = "exam-$examId"
    }

    data class Submission(val submissionId: String) : ConversionSource {
        override val jobId: String get() = "sub-$submissionId"
    }
}

// Added in 4.2.0 — token and cost accounting of a job (the $50 monthly cap is summed server-side).
data class ConversionUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val costUsd: Double,
)

// Added in 4.2.0 — one `examConversions/<jobId>` document.
data class ExamConversion(
    val id: String,
    val source: ConversionSource,
    val kind: ExamKind,
    val status: ConversionStatus,
    /** The provider the admin picked for the latest run (`model.provider`). */
    val provider: AIProvider?,
    val attempts: Int,
    /** Last failure (a plain string on the server), shown on the failed-jobs list. */
    val error: String?,
    val usage: ConversionUsage?,
    /** Display names for the review screen ("BOZOK › … › 2019 FİNAL"); empty when unknown. */
    val placementNames: List<String>,
    val createdAt: Instant?,
    val readyAt: Instant?,
    /** The exam's original pages (`files [{storagePath, order}]`), for the side-by-side review. */
    val files: List<SubmissionFileRef> = emptyList(),
    /** The server's suggested country-wide course identity (`placement.subjectId` / `subjectName`). */
    val suggestedSubjectId: String? = null,
    val suggestedSubjectName: String? = null,
    /** The exam's title before conversion (`placement.originalTitle`, else the last placement name). */
    val originalTitle: String? = null,
    /** The list title the server suggests (K28); `null` when none matched and the admin must pick one. */
    val suggestedTitle: String? = null,
)

// Added in 4.2.0 — what a bulk conversion covers: every unconverted exam of one course or of a whole department.
sealed interface BulkConversionScope {
    data class Course(val id: String) : BulkConversionScope
    data class Department(val id: String) : BulkConversionScope
}

// Added in 4.2.0 — the answer of `requestBulkConversion`: for a dry run only the numbers, otherwise also the job ids.
data class BulkConversionEstimate(
    val examCount: Int,
    val estimatedCostUsd: Double,
    val jobIds: List<String>,
)

// Added in 4.2.0 — the month's AI spend against the $50 cap (`aiUsage/<YYYY-MM>`, server-written; K11).
data class AIMonthlyUsage(
    /** `YYYY-MM` in Europe/Istanbul. */
    val month: String,
    val costUsd: Double,
) {
    /** Spend as a share of the cap, clamped to 0…1 for the gauge. */
    val fraction: Double get() = (costUsd / CAP_USD).coerceIn(0.0, 1.0)

    /** Whether adding [extra] dollars would pass the cap. */
    fun exceedsCap(adding: Double): Boolean = costUsd + adding > CAP_USD

    companion object {
        /** The monthly cap in US dollars (owner decision K11). */
        const val CAP_USD = 50.0

        /** `YYYY-MM` of [instant] in Europe/Istanbul (UTC+3 all year), as `usageMonthKey` on the server. */
        fun monthKey(instant: Instant): String {
            val date = instant.atOffset(ZoneOffset.ofHours(3))
            return "%04d-%02d".format(date.year, date.monthValue)
        }
    }
}

// Added in 4.2.0 — the answer of `publishExamConversion`.
data class PublishedExamResult(
    val examId: String,
    val questionCount: Int,
)
