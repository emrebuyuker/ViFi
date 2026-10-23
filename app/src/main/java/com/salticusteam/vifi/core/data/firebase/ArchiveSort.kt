package com.salticusteam.vifi.core.data.firebase

import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import java.text.Collator
import java.util.Locale

/**
 * Display order of archive lists (iOS `ArchiveParser.sorted` / `compare`).
 *
 * Names are sorted in Turkish alphabetical order (Ç after C, Ş after S), case-insensitively and numerically
 * aware ("2. Sınıf" < "10. Sınıf"); exams are listed newest first ("2019 FİNAL" before "2002 VİZE"). Diacritics
 * stay significant on purpose, like iOS, which leaves out `.diacriticInsensitive`.
 */
object ArchiveSort {

    private val TURKISH: Locale = Locale.forLanguageTag("tr-TR")

    /** A run of digits or a run of anything else; numbers compare by value, text by the collator. */
    private val TOKEN = "[0-9]+|[^0-9]+".toRegex()

    /** Catalog levels ascending; the exam level reversed. Stable for names that compare equal. */
    fun sorted(items: List<ArchiveItem>, level: ArchiveLevel): List<ArchiveItem> {
        val comparator = nameComparator()
        val ascending = items.sortedWith { lhs, rhs -> comparator.compare(lhs.name, rhs.name) }
        return if (level == ArchiveLevel.EXAM) ascending.asReversed() else ascending
    }

    /** A fresh comparator per call: `Collator` instances are not thread-safe. */
    fun nameComparator(): Comparator<String> {
        // SECONDARY: case-insensitive, accent-sensitive (iOS `.caseInsensitive` without `.diacriticInsensitive`).
        val collator = Collator.getInstance(TURKISH).apply { strength = Collator.SECONDARY }
        return Comparator { lhs, rhs -> compare(lhs, rhs, collator) }
    }

    private fun compare(lhs: String, rhs: String, collator: Collator): Int {
        val left = TOKEN.findAll(lhs).map { it.value }.iterator()
        val right = TOKEN.findAll(rhs).map { it.value }.iterator()
        while (left.hasNext() && right.hasNext()) {
            val a = left.next()
            val b = right.next()
            val result = if (a[0].isAsciiDigit() && b[0].isAsciiDigit()) compareNumbers(a, b) else collator.compare(a, b)
            if (result != 0) return result
        }
        return left.hasNext().compareTo(right.hasNext())
    }

    /** Compares two digit runs by value without overflow: fewer significant digits is smaller. */
    private fun compareNumbers(a: String, b: String): Int {
        val x = a.trimStart('0')
        val y = b.trimStart('0')
        return if (x.length != y.length) x.length.compareTo(y.length) else x.compareTo(y)
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}
