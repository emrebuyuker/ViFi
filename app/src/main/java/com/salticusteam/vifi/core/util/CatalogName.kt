package com.salticusteam.vifi.core.util

import java.util.Locale

// Added in 4.2.0 — display name of a catalog node created from a submission (iOS `CatalogName`, server `catalogDisplayName`).
/**
 * Whitespace collapsed and Turkish upper case ("Güç elektroniği devreleri" → "GÜÇ ELEKTRONİĞİ DEVRELERİ"), like the
 * rest of the archive. Ids (slugs) are unaffected. Idempotent.
 */
object CatalogName {
    private val TURKISH: Locale = Locale.forLanguageTag("tr")
    private val WHITESPACE = Regex("\\s+")

    fun display(name: String): String =
        name.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ").uppercase(TURKISH)
}
