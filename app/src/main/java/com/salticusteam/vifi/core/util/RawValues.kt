package com.salticusteam.vifi.core.util

/**
 * Lenient readers for raw Firestore / RTDB / callable values (4.2.0 parsers). Firestore integers arrive as `Long`,
 * callable numbers as `Int`, `Long` or `Double`; iOS's `as? Int` accepts a whole `Double`, so these do too.
 */
internal object RawValues {

    /** A whole number (`Int`, `Long`, or an integral finite `Double`/`Float` in `Int` range); `null` otherwise. */
    fun int(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        is Short -> value.toInt()
        is Byte -> value.toInt()
        is Double -> value.takeIf { it.isFinite() && it == Math.rint(it) && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }?.toInt()
        is Float -> int(value.toDouble())
        else -> null
    }

    /** Any number as a `Double`; `null` for anything else (a Boolean included). */
    fun double(value: Any?): Double? = (value as? Number)?.toDouble()?.takeIf { it.isFinite() }

    /** A string trimmed of whitespace and newlines, or `null` when it is not a string or is blank. */
    fun nonEmpty(value: Any?): String? = (value as? String)?.trim()?.takeIf { it.isNotEmpty() }

    /** The non-blank strings of a list (trimmed), dropping everything else. */
    fun strings(value: Any?): List<String> = (value as? List<*>).orEmpty().mapNotNull(::nonEmpty)

    /** The map entries of a list. */
    fun maps(value: Any?): List<Map<*, *>> = (value as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
}
