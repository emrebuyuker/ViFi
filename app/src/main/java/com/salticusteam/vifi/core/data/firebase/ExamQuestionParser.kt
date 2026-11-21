package com.salticusteam.vifi.core.data.firebase

import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.QuestionAnswer
import com.salticusteam.vifi.core.model.QuestionChoice
import com.salticusteam.vifi.core.model.QuestionFigure
import com.salticusteam.vifi.core.util.QuestionLabel
import com.salticusteam.vifi.core.util.RawValues
import java.time.Instant

/**
 * Turns raw question documents (`questions/<examId>-<nnn>`, or a draft under `examConversions/<jobId>/questions/<nnn>`)
 * into [ExamQuestion]s (iOS `ExamQuestionParser`, lockstep with the shared schema.js).
 *
 * Pure and Firestore-free like [FirestoreArchiveParser]: callers convert `Timestamp` values to [Instant] first. A
 * document that cannot be shown safely (no text, no answer text, no or bad `order`, a newer `schemaVersion`) is skipped
 * rather than shown half-broken; the rest degrades softly (no label → the order, no speech → the text, bad choice or
 * figure entries dropped, difficulty clamped to 1…3 with 2 as default).
 */
object ExamQuestionParser {

    /** The newest published shape this build understands. */
    const val SUPPORTED_SCHEMA_VERSION = 1

    object Field {
        const val SCHEMA_VERSION = "schemaVersion"
        const val EXAM_ID = "examId"
        const val ORDER = "order"
        const val LABEL = "label"
        const val TEXT = "text"
        const val SPEECH = "speech"
        const val CHOICES = "choices"
        const val FIGURES = "figures"
        const val ANSWER = "answer"
        const val SOLUTION = "solution"
        const val TOPICS = "topics"
        const val DIFFICULTY = "difficulty"
        const val CONFIDENCE = "confidence"
        const val NEEDS_REVIEW = "needsReview"
        const val UPDATED_AT = "updatedAt"
        const val REGENERATION_LEASE_UNTIL = "regenerationLeaseUntil"
        const val REGENERATION_ERROR = "regenerationError"
        const val STORAGE_PATH = "storagePath"
        const val WIDTH = "width"
        const val HEIGHT = "height"
        const val CHOICE = "choice"
    }

    /** The questions of one exam in reading order (`order`, then id). [examId] fills drafts, which do not store it. */
    fun questions(docs: List<Pair<String, Map<String, Any?>>>, examId: String): List<ExamQuestion> =
        docs.mapNotNull { (id, data) -> question(id, data, examId) }
            .sortedWith(compareBy<ExamQuestion> { it.order }.thenBy { it.id })

    fun question(id: String, data: Map<String, Any?>, fallbackExamId: String): ExamQuestion? {
        val version = RawValues.int(data[Field.SCHEMA_VERSION])
        if (version != null && version > SUPPORTED_SCHEMA_VERSION) return null
        val order = RawValues.int(data[Field.ORDER])?.takeIf { it >= 1 } ?: return null
        val text = RawValues.nonEmpty(data[Field.TEXT]) ?: return null
        val answer = answer(data[Field.ANSWER]) ?: return null
        return ExamQuestion(
            id = id,
            examId = RawValues.nonEmpty(data[Field.EXAM_ID]) ?: fallbackExamId,
            order = order,
            label = RawValues.nonEmpty(data[Field.LABEL])?.let(QuestionLabel::normalize) ?: order.toString(),
            text = text,
            speech = RawValues.nonEmpty(data[Field.SPEECH]) ?: text,
            choices = choices(data[Field.CHOICES]),
            figures = figures(data[Field.FIGURES]),
            answer = answer,
            solution = RawValues.strings(data[Field.SOLUTION]),
            topics = RawValues.strings(data[Field.TOPICS]),
            difficulty = (RawValues.int(data[Field.DIFFICULTY]) ?: DEFAULT_DIFFICULTY).coerceIn(1, 3),
            confidence = (data[Field.CONFIDENCE] as? Number)?.toDouble()?.coerceIn(0.0, 1.0),
            needsReview = data[Field.NEEDS_REVIEW] as? Boolean ?: false,
            updatedAt = data[Field.UPDATED_AT] as? Instant,
            regeneratingUntil = data[Field.REGENERATION_LEASE_UNTIL] as? Instant,
            regenerationError = RawValues.nonEmpty(data[Field.REGENERATION_ERROR]),
        )
    }

    // Helpers

    private const val DEFAULT_DIFFICULTY = 2

    private fun answer(value: Any?): QuestionAnswer? {
        val map = value as? Map<*, *> ?: return null
        val text = RawValues.nonEmpty(map[Field.TEXT]) ?: return null
        return QuestionAnswer(text = text, choice = RawValues.nonEmpty(map[Field.CHOICE]), speech = RawValues.nonEmpty(map[Field.SPEECH]))
    }

    private fun choices(value: Any?): List<QuestionChoice> = RawValues.maps(value).mapNotNull { entry ->
        val label = RawValues.nonEmpty(entry[Field.LABEL]) ?: return@mapNotNull null
        val text = RawValues.nonEmpty(entry[Field.TEXT]) ?: return@mapNotNull null
        QuestionChoice(label, text)
    }

    private fun figures(value: Any?): List<QuestionFigure> = RawValues.maps(value).mapNotNull { entry ->
        val path = RawValues.nonEmpty(entry[Field.STORAGE_PATH]) ?: return@mapNotNull null
        QuestionFigure(
            storagePath = path,
            width = RawValues.int(entry[Field.WIDTH])?.takeIf { it > 0 },
            height = RawValues.int(entry[Field.HEIGHT])?.takeIf { it > 0 },
        )
    }
}
