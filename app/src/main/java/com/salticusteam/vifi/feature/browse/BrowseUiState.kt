package com.salticusteam.vifi.feature.browse

import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchivePath
import java.text.Normalizer
import java.util.Locale

/**
 * State of an archive list: the universities on the home screen ([path] is root) and every drill-down level.
 *
 * [items] is `null` until the first successful load, so a failed refresh can keep the entries already shown
 * while an empty screen switches to [error].
 */
data class BrowseUiState(
    val path: ArchivePath = ArchivePath.ROOT,
    val isLoading: Boolean = true,
    val items: List<ArchiveItem>? = null,
    val error: String? = null,
    val searchText: String = "",
) {
    /** The search text without surrounding whitespace. */
    val searchQuery: String get() = searchText.trim()

    /** Whether a search query is narrowing down the list. */
    val isFiltering: Boolean get() = searchQuery.isNotEmpty()

    /** The loaded entries whose names match the search query; empty unless loaded. */
    val visibleItems: List<ArchiveItem>
        get() {
            val loaded = items ?: return emptyList()
            val query = searchKey(searchQuery)
            if (query.isEmpty()) return loaded
            return loaded.filter { searchKey(it.name).contains(query) }
        }

    private companion object {
        val TURKISH: Locale = Locale.forLanguageTag("tr-TR")
        val COMBINING_MARKS = "\\p{Mn}+".toRegex()

        /**
         * Folds Turkish text for matching: case- and diacritic-insensitive, dotted and dotless i alike, so
         * "kocaeli" finds "KOCAELİ", "dumlupinar" finds "DUMLUPINAR" and "isletme" finds "İŞLETME".
         */
        fun searchKey(text: String): String {
            val lowered = text.lowercase(TURKISH).replace('ı', 'i')
            return Normalizer.normalize(lowered, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        }
    }
}
