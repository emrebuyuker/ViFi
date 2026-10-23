package com.salticusteam.vifi.core.util

/**
 * Compares dotted numeric version strings ("2.0.10" > "2.0.9") (iOS `AppVersion`).
 * Missing trailing components count as zero ("3.0" == "3.0.0"); a non-numeric component keeps its leading
 * digits ("1-beta" → 1) or counts as zero when it has none.
 */
object AppVersion {

    /** Whether [candidate] is a strictly later version than [other]. */
    fun isNewer(candidate: String, other: String): Boolean {
        val lhs = numericComponents(candidate)
        val rhs = numericComponents(other)
        for (index in 0 until maxOf(lhs.size, rhs.size)) {
            val a = lhs.getOrElse(index) { 0 }
            val b = rhs.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** "3.0.1" → [3, 0, 1]. */
    fun numericComponents(version: String): List<Int> =
        version.trim().split('.').map { component ->
            component.takeWhile { it in '0'..'9' }.toIntOrNull() ?: 0
        }
}
