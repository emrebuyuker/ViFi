package com.salticusteam.vifi.core.util

/**
 * Turns a display name into a stable, topic-safe slug (`[a-z0-9-]`), the canonical id of a university /
 * faculty / department the user types when it is not on the bundled list (iOS `Slug`).
 *
 * Must stay in lockstep with iOS and the bundled catalogs' ids, so a free-typed name that matches a listed one
 * yields the same id, and the ids are safe to use directly in FCM topic names.
 */
object Slug {

    /** Turkish letters mapped to their ASCII base before lowercasing (plain lowercasing mishandles İ/I). */
    private val TURKISH_MAP = mapOf(
        'İ' to 'i', 'I' to 'i', 'ı' to 'i',
        'Ğ' to 'g', 'ğ' to 'g',
        'Ü' to 'u', 'ü' to 'u',
        'Ş' to 's', 'ş' to 's',
        'Ö' to 'o', 'ö' to 'o',
        'Ç' to 'c', 'ç' to 'c',
    )

    fun make(input: String): String {
        val lowered = input.map { TURKISH_MAP[it] ?: it }.joinToString("").lowercase()

        // Keep ASCII letters/digits; collapse every other run into a single dash, with no leading or trailing
        // dash (the dash is only flushed right before the next kept character).
        val slug = StringBuilder()
        var pendingDash = false
        for (char in lowered) {
            if (char in 'a'..'z' || char in '0'..'9') {
                if (pendingDash) {
                    slug.append('-')
                    pendingDash = false
                }
                slug.append(char)
            } else if (slug.isNotEmpty()) {
                pendingDash = true
            }
        }
        return if (slug.isEmpty()) "unknown" else slug.toString()
    }
}
