package com.salticusteam.vifi.core.model

import java.text.Normalizer
import java.util.Locale

/**
 * Turkish-insensitive name matching for aligning bundled-list names (profile, add-exam pickers) with archive
 * catalog names (iOS `ArchiveNameMatch`): case- and diacritic-insensitive, dotted and dotless i alike.
 *
 * Pure Kotlin, shared by the lesson picker, the add-exam tier estimate and the moderation placement. Unlike
 * [com.salticusteam.vifi.core.data.catalog.UniversityCatalog.filtered] it folds `ı` to `i`, because archive
 * names are typed by hand and spelled inconsistently.
 */
object ArchiveNameMatch {

    private val turkish = Locale.forLanguageTag("tr-TR")
    private val combiningMarks = "\\p{Mn}+".toRegex()

    /** Trailing academic suffixes an archive department may carry ("MATEMATİK BÖLÜMÜ"), already folded. */
    private val departmentSuffixes = listOf(" bolumu", " bolum")

    /** Whether [lhs] and [rhs] are the same name after folding. */
    fun matches(lhs: String, rhs: String): Boolean = key(lhs) == key(rhs)

    /**
     * Whether the archive department [archive] is the one the bundled [profile] name refers to, tolerating the
     * archive's trailing suffix (so "MATEMATİK BÖLÜMÜ" matches "Matematik", while "MATEMATİK MÜHENDİSLİĞİ" —
     * which keeps a distinct word — does not).
     */
    fun matchesDepartment(archive: String, profile: String): Boolean =
        departmentKey(archive) == departmentKey(profile)

    /** Whether [haystack] contains [needle] after folding (for search). */
    fun contains(haystack: String, needle: String): Boolean = key(haystack).contains(key(needle))

    /** The folded comparison key: Turkish lowercase, `ı` → `i`, diacritics stripped. */
    fun key(text: String): String {
        val lowered = text.lowercase(turkish).replace('ı', 'i')
        return Normalizer.normalize(lowered, Normalizer.Form.NFD).replace(combiningMarks, "")
    }

    /** A folded key with one whole trailing " bölümü" / " bölüm" word dropped (never a word stem). */
    private fun departmentKey(text: String): String {
        val key = key(text)
        val suffix = departmentSuffixes.firstOrNull { key.endsWith(it) } ?: return key
        return key.dropLast(suffix.length)
    }
}
