package com.salticusteam.vifi.core.data.catalog

import com.salticusteam.vifi.core.model.CatalogItem
import java.io.InputStream
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * The bundled lists of Turkish universities, faculties and departments that back the profile pickers
 * (iOS UniversityCatalog).
 *
 * Each entry carries a canonical id so a picked value can be targeted by notification segments regardless of
 * casing or spelling. The pickers always allow free text (slugged best-effort), so a missing or incomplete
 * list only degrades convenience, never blocks the user. Plain value type: filtering is unit-testable.
 */
data class UniversityCatalog(
    val universities: List<CatalogItem>,
    val faculties: List<CatalogItem>,
    val departments: List<CatalogItem>,
) {
    /** Universities whose name contains [query] (Turkish-insensitive); the whole list when the query is blank. */
    fun universities(query: String): List<CatalogItem> = filtered(universities, query)

    /** Faculties whose name contains [query] (Turkish-insensitive); the whole list when the query is blank. */
    fun faculties(query: String): List<CatalogItem> = filtered(faculties, query)

    /** Departments whose name contains [query] (Turkish-insensitive); the whole list when the query is blank. */
    fun departments(query: String): List<CatalogItem> = filtered(departments, query)

    companion object {
        val EMPTY = UniversityCatalog(emptyList(), emptyList(), emptyList())

        const val UNIVERSITIES_ASSET = "catalog/turkish-universities.json"
        const val FACULTIES_ASSET = "catalog/turkish-faculties.json"
        const val DEPARTMENTS_ASSET = "catalog/turkish-departments.json"

        private val turkish = Locale.forLanguageTag("tr-TR")
        private val combiningMarks = "\\p{Mn}+".toRegex()
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Items whose name contains [query], matched case- and diacritic-insensitively with Turkish casing (so
         * "isl" finds "İşletme" and "bogazici" finds "Boğaziçi"); the whole list when the query is blank.
         * Like iOS, dotless `ı` is a letter of its own and is not folded to `i`.
         */
        fun filtered(items: List<CatalogItem>, query: String): List<CatalogItem> {
            val needle = normalized(query)
            if (needle.isEmpty()) return items
            return items.filter { normalized(it.name).contains(needle) }
        }

        /** iOS `ArchiveParser.compare`: Turkish, case-insensitive, with digit runs compared numerically. */
        fun compareNames(lhs: String, rhs: String): Int {
            val collator = Collator.getInstance(turkish).apply { strength = Collator.SECONDARY }
            val left = chunks(lhs)
            val right = chunks(rhs)
            for (index in 0 until minOf(left.size, right.size)) {
                val a = left[index]
                val b = right[index]
                val result = if (a.first().isDigit() && b.first().isDigit()) {
                    a.toBigInteger().compareTo(b.toBigInteger())
                } else {
                    collator.compare(a, b)
                }
                if (result != 0) return result
            }
            return left.size.compareTo(right.size)
        }

        /**
         * Builds the catalog from the three bundled lists, sorted by name. [open] returns an asset's stream;
         * a missing or malformed list becomes empty and is reported through [onError] (the pickers then fall
         * back to free text).
         */
        fun load(
            open: (String) -> InputStream,
            onError: (asset: String, error: Throwable) -> Unit = { _, _ -> },
        ) = UniversityCatalog(
            universities = loadList(UNIVERSITIES_ASSET, open, onError),
            faculties = loadList(FACULTIES_ASSET, open, onError),
            departments = loadList(DEPARTMENTS_ASSET, open, onError),
        )

        @OptIn(ExperimentalSerializationApi::class)
        private fun loadList(
            asset: String,
            open: (String) -> InputStream,
            onError: (String, Throwable) -> Unit,
        ): List<CatalogItem> =
            try {
                open(asset).use { json.decodeFromStream<List<CatalogItem>>(it) }
                    .sortedWith { a, b -> compareNames(a.name, b.name) }
            } catch (e: Exception) {
                onError(asset, e)
                emptyList()
            }

        private fun normalized(string: String): String {
            val lowered = string.trim().lowercase(turkish)
            return Normalizer.normalize(lowered, Normalizer.Form.NFD).replace(combiningMarks, "")
        }

        /** Splits into alternating digit / non-digit runs ("Kilis 7 Aralık" → ["Kilis ", "7", " Aralık"]). */
        private fun chunks(string: String): List<String> {
            if (string.isEmpty()) return emptyList()
            val result = mutableListOf<String>()
            val current = StringBuilder().append(string[0])
            for (index in 1 until string.length) {
                val char = string[index]
                if (char.isDigit() == current.last().isDigit()) {
                    current.append(char)
                } else {
                    result += current.toString()
                    current.clear().append(char)
                }
            }
            result += current.toString()
            return result
        }
    }
}
