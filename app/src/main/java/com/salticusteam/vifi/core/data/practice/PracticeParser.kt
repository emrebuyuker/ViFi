package com.salticusteam.vifi.core.data.practice

import com.salticusteam.vifi.core.data.firebase.ExamQuestionParser
import com.salticusteam.vifi.core.model.PracticeBatch
import com.salticusteam.vifi.core.model.PracticeQuestion
import com.salticusteam.vifi.core.util.RawValues
import java.time.Instant
import kotlin.math.floor

/**
 * Turns the `nextPracticeQuestions` response into a [PracticeBatch] (iOS `PracticeParser.batch`). Pure and
 * Firebase-free. Response: `{ questions: [<published question> + id, examTitle, universityName], poolSize,
 * remainingToday | null, limitReached }`; `updatedAt` arrives as epoch milliseconds (JSON has no timestamp).
 */
object PracticeParser {

    object Field {
        const val QUESTIONS = "questions"
        const val ID = "id"
        const val EXAM_ID = "examId"
        const val ORDER = "order"
        const val EXAM_TITLE = "examTitle"
        const val UNIVERSITY_NAME = "universityName"
        const val POOL_SIZE = "poolSize"
        const val REMAINING_TODAY = "remainingToday"
        const val LIMIT_REACHED = "limitReached"
    }

    /** The callable's response; `null` when it is not a map. A question that cannot be shown is skipped. */
    fun batch(data: Any?): PracticeBatch? {
        val map = data as? Map<*, *> ?: return null
        return PracticeBatch(
            questions = RawValues.maps(map[Field.QUESTIONS]).mapNotNull(::question),
            poolSize = RawValues.int(map[Field.POOL_SIZE]) ?: 0,
            remainingToday = RawValues.int(map[Field.REMAINING_TODAY])?.coerceAtLeast(0),
            limitReached = map[Field.LIMIT_REACHED] as? Boolean ?: false,
        )
    }

    /** One question of the response: the published shape plus `examTitle` / `universityName`. */
    fun question(data: Map<*, *>): PracticeQuestion? {
        val examId = data[Field.EXAM_ID] as? String ?: ""
        val order = RawValues.int(data[Field.ORDER]) ?: 0
        val fallbackId = if (examId.isEmpty() || order < 1) null else "$examId-${"%03d".format(order)}"
        val id = (data[Field.ID] as? String)?.takeIf { it.isNotEmpty() } ?: fallbackId ?: return null
        val fields = data.entries.associate { (key, value) -> key.toString() to value }.toMutableMap()
        fields[ExamQuestionParser.Field.UPDATED_AT] = epochMillis(data[ExamQuestionParser.Field.UPDATED_AT])
        val question = ExamQuestionParser.question(id, fields, examId) ?: return null
        return PracticeQuestion(
            question = question,
            examTitle = RawValues.nonEmpty(data[Field.EXAM_TITLE]),
            universityName = RawValues.nonEmpty(data[Field.UNIVERSITY_NAME]),
        )
    }

    /** Epoch milliseconds (whole or fractional) as an [Instant]; `null` for anything else. */
    private fun epochMillis(value: Any?): Instant? {
        val millis = RawValues.double(value) ?: return null
        val whole = floor(millis)
        return Instant.ofEpochMilli(whole.toLong()).plusNanos(((millis - whole) * NANOS_PER_MILLI).toLong())
    }

    private const val NANOS_PER_MILLI = 1_000_000.0
}
