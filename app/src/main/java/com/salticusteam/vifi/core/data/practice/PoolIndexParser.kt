package com.salticusteam.vifi.core.data.practice

import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PoolIndex
import com.salticusteam.vifi.core.util.RawValues

/**
 * Turns `questionPoolIndex/<subjectId>` into a [PoolIndex] (iOS `PracticeParser.index`). Pure and Firestore-free.
 *
 * Shape: `{ subjectId, name, updatedAt, types: { <exam type>: { tr: n, "u-<universityId>": n, "c-<courseId>": n } } }`.
 * Unknown or non-practice types, malformed maps and non-positive counts are dropped.
 */
object PoolIndexParser {

    object Field {
        const val NAME = "name"
        const val TYPES = "types"
    }

    /** The index content, or `null` when it lists no practice-eligible type with a question. */
    fun index(data: Map<String, Any?>, subjectId: String): PoolIndex? {
        val rawTypes = data[Field.TYPES] as? Map<*, *> ?: return null
        val types = mutableMapOf<ExamType, Map<String, Int>>()
        for ((rawType, rawCounts) in rawTypes) {
            val type = ExamType.fromRaw(rawType as? String)?.takeIf { it.isPracticeEligible } ?: continue
            val counts = rawCounts as? Map<*, *> ?: continue
            val valid = counts.entries.mapNotNull { (key, value) ->
                val scopeKey = key as? String ?: return@mapNotNull null
                val count = RawValues.int(value)?.takeIf { it > 0 } ?: return@mapNotNull null
                scopeKey to count
            }.toMap()
            if (valid.isNotEmpty()) types[type] = valid
        }
        if (types.isEmpty()) return null
        return PoolIndex(subjectId = subjectId, name = data[Field.NAME] as? String, types = types)
    }
}
