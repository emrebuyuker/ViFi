package com.salticusteam.vifi.core.model

import java.time.Instant

// The structured-exam question (4.2.0, iOS `ExamQuestion`): the read side of the server contract in
// `firebase/functions/conversions/schema.js`. Published questions live flat in Firestore `questions/<examId>-<nnn>`
// (read by +90 users and admins); drafts live under `examConversions/<jobId>/questions/<nnn>` and carry the
// review-only fields ([ExamQuestion.confidence], [ExamQuestion.needsReview], the regeneration lease).

// Added in 4.2.0 — one answer option of a multiple-choice question ("A", "B", …).
data class QuestionChoice(
    val label: String,
    /** Markdown + LaTeX (`$…$`, `$$…$$`). */
    val text: String,
)

// Added in 4.2.0 — a figure cropped from the original page (`questionFigures/<jobId>/<nnn>-<k>.jpg`).
/**
 * [storagePath] is used as is (never built by the app) and is the image cache key: a regenerated question gets a new
 * path. [width] / [height] are pixels so the layout can reserve space before the download; `null` when unknown.
 */
data class QuestionFigure(
    val storagePath: String,
    val width: Int?,
    val height: Int?,
)

// Added in 4.2.0 — the answer revealed by "Cevabı Göster".
data class QuestionAnswer(
    /** Markdown + LaTeX. */
    val text: String,
    /** The correct choice label for multiple choice. */
    val choice: String?,
    /** The answer as plain Turkish for TalkBack; `null` falls back to [text] (`MathTextTokenizer.plainText`). */
    val speech: String?,
)

// Added in 4.2.0 — a question as the app shows it (published or draft).
data class ExamQuestion(
    /** Published: `<examId>-<nnn>` (permanent). Draft: `<nnn>`. */
    val id: String,
    val examId: String,
    /** 1-based position in the exam. */
    val order: Int,
    /** The number printed on the paper ("1", "2a"), normalized by `QuestionLabel`; the card title is "<label>. Soru". */
    val label: String,
    /** Markdown + LaTeX, transcribed as written. */
    val text: String,
    /** Plain-text version of [text] for TalkBack (formulas in words); [text] when the document has none. */
    val speech: String,
    val choices: List<QuestionChoice>,
    val figures: List<QuestionFigure>,
    val answer: QuestionAnswer,
    /** Worked solution steps (Markdown + LaTeX). */
    val solution: List<String>,
    /** Topic slugs (not shown in 4.2.0). */
    val topics: List<String>,
    /** 1 easy … 3 hard. */
    val difficulty: Int,
    /** Drafts only: how sure the model was (0…1). */
    val confidence: Double? = null,
    /** Drafts only: the model asks for a careful look. */
    val needsReview: Boolean = false,
    val updatedAt: Instant? = null,
    /** Drafts only: the end of the server's lease while a single-question regeneration runs. */
    val regeneratingUntil: Instant? = null,
    /** Drafts only: why the last single-question regeneration failed (cleared when a new one starts). */
    val regenerationError: String? = null,
) {
    /** Whether a regeneration is running at [now] (the lease has not expired). */
    fun isRegenerating(now: Instant): Boolean = regeneratingUntil?.isAfter(now) ?: false
}

// Added in 4.2.0 — the AI provider an admin picks for every conversion action (owner decision K9; no default anywhere).
enum class AIProvider(val raw: String) {
    CLAUDE("claude"),
    GEMINI("gemini");

    companion object {
        fun fromRaw(raw: String?): AIProvider? = entries.firstOrNull { it.raw == raw }
    }
}
