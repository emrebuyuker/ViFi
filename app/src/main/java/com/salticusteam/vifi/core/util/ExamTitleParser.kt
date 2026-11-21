package com.salticusteam.vifi.core.util

import com.salticusteam.vifi.core.model.ExamNameCatalog
import com.salticusteam.vifi.core.model.ExamType
import java.text.Normalizer

// Added in 4.2.0 — splits an archive exam title into type + year and converts it to a list title (iOS `ExamTitleParser`).
/**
 * A port of `parseExamTitle` / `canonicalExamTitle` in the shared schema.js; the server, iOS and this file run the same
 * cases. The year is the first 19xx/20xx slug word; an academic-year range gives its second year (K29). Types are
 * matched on the title's slug in priority order: study materials first ("FİNAL ÇALIŞMA SORULARI" is material), then
 * the specific exam kinds, then vize/final, so "2019 YAZ OKULU FİNAL" is a final and "YAZ OKULU" alone is a
 * summer-school exam. Needles match whole slug words only ("YEDEK SINAV" is not `ek-sinav`).
 */
object ExamTitleParser {

    data class Result(val examType: ExamType?, val year: Int?)

    /** The list title an archive title converts to (K28). */
    data class CanonicalTitle(val title: String, val entryId: String, val examType: ExamType)

    /** Checked in order; the first hit wins. */
    private val TYPE_KEYS: List<Pair<ExamType, List<String>>> = listOf(
        ExamType.CALISMA_SORULARI to listOf("calisma-sorulari"),
        ExamType.COZUMLU_SORULAR to listOf("cozumlu-sorular"),
        ExamType.LABORATUVAR to listOf("laboratuvar", "laboratuar", "lab"),
        ExamType.ODEV to listOf("odev", "odevi", "odevler"),
        ExamType.PROJE to listOf("proje", "projesi"),
        ExamType.BUTUNLEME to listOf("butunleme"),
        ExamType.MAZERET to listOf("mazeret"),
        ExamType.TEK_DERS to listOf("tek-ders"),
        ExamType.EK_SINAV to listOf("ek-sinav", "ek-sinavi"),
        ExamType.MUAFIYET to listOf("muafiyet"),
        ExamType.SEVIYE_TESPIT to listOf("seviye-tespit"),
        ExamType.UYGULAMA to listOf("uygulama-sinavi", "uygulama-sinav"),
        ExamType.KISA_SINAV to listOf("kisa-sinav", "kisa-sinavlari", "quiz"),
        ExamType.ARA_SINAV to listOf("ara-sinav", "arasinav"),
        ExamType.VIZE to listOf("vize"),
        ExamType.FINAL to listOf("final"),
        ExamType.YAZ_OKULU to listOf("yaz-okulu"),
    )

    fun parse(title: String): Result {
        val key = Slug.make(title)
        val tokens = key.split('-')
        val padded = "-$key-"
        val type = TYPE_KEYS.firstOrNull { (_, needles) -> needles.any { padded.contains("-$it-") } }?.first
        return Result(examType = type, year = year(tokens)?.toInt())
    }

    /**
     * The list title for [title] (K28), or `null` when no list name matches. Title = year + list name + sequence
     * number: the year as in [parse]; the name is the base type by [parse]'s priority without plain "yaz okulu" (a title
     * that also says "yaz okulu" maps vize / ara sınav / final to the YAZ OKULU entries); the sequence number is a 1–99
     * number right after the matched type words, and a kısa sınav numbered 1–3 takes the list's own "KISA SINAV 1/2/3"
     * name instead (K29). Every other word is dropped.
     */
    fun canonicalTitle(title: String): CanonicalTitle? {
        val tokens = Slug.make(title).split('-')
        var match: Pair<ExamType, Int>? = null
        search@ for ((type, needles) in TYPE_KEYS) {
            if (type == ExamType.YAZ_OKULU) continue
            for (needle in needles) {
                val words = needle.split('-')
                val index = firstIndex(words, tokens)
                if (index != null) {
                    match = type to index + words.size
                    break@search
                }
            }
        }
        val (type, end) = match ?: return null
        val isSummer = firstIndex(listOf("yaz", "okulu"), tokens) != null
        val summerEntry = if (isSummer) ExamNameCatalog.entry("yaz-okulu-${type.raw}") else null
        var entry = summerEntry ?: baseEntry(type) ?: return null

        var number: Int? = null
        if (end < tokens.size && tokens[end].length <= 2) {
            number = tokens[end].takeIf { t -> t.all { it in '0'..'9' } }?.toIntOrNull()?.takeIf { it >= 1 }
        }
        val numbered = number?.let { ExamNameCatalog.entry("kisa-sinav-$it") }
        if (entry.id == "kisa-sinav" && numbered != null) {
            entry = numbered
            number = null
        }
        val parts = listOfNotNull(year(tokens), entry.name, number?.toString())
        return CanonicalTitle(title = parts.joinToString(" "), entryId = entry.id, examType = entry.examType)
    }

    /** The first 19xx/20xx word, or the second year of an academic-year range ("2017-2018" → "2018", K29). */
    private fun year(tokens: List<String>): String? {
        val index = tokens.indexOfFirst(::isYear)
        if (index < 0) return null
        if (index + 1 < tokens.size && isYear(tokens[index + 1])) return tokens[index + 1]
        return tokens[index]
    }

    private fun isYear(token: String): Boolean =
        token.length == 4 && (token.startsWith("19") || token.startsWith("20")) && token.all { it in '0'..'9' }

    private fun baseEntry(type: ExamType): ExamNameCatalog.Entry? = ExamNameCatalog.entries.firstOrNull { entry ->
        entry.examType == type && !entry.id.startsWith("yaz-okulu-") && !entry.id.startsWith("kisa-sinav-")
    }

    private fun firstIndex(words: List<String>, tokens: List<String>): Int? {
        if (words.isEmpty() || words.size > tokens.size) return null
        return (0..(tokens.size - words.size)).firstOrNull { tokens.subList(it, it + words.size) == words }
    }
}

// Added in 4.2.0 — a title from the exam-name list (K28): `[<year> ]<list name>[ <1–99>]` (iOS `ExamListTitle`).
/**
 * [parse] mirrors `parseListTitle` / `validateExamTitle` in the shared schema.js: the name must be written exactly as in
 * the list (Turkish upper case), one 19xx/20xx year at most, a sequence number 1–99 without a leading zero, and a
 * numbered list name ("KISA SINAV 2") takes no extra number. The admin's title picker builds one from its parts.
 */
data class ExamListTitle(val year: Int?, val entryId: String, val number: Int?) {

    /** The normalised title, as the server stores it. */
    val title: String
        get() = listOfNotNull(year?.toString(), ExamNameCatalog.entry(entryId)?.name.orEmpty(), number?.toString())
            .joinToString(" ")

    companion object {
        private val WHITESPACE = Regex("\\s+")

        /** [title] as a list title, or `null` when it is not one (the server's validation). */
        fun parse(title: String): ExamListTitle? {
            var words = Normalizer.normalize(title, Normalizer.Form.NFC).split(WHITESPACE).filter { it.isNotEmpty() }
            val year = words.firstOrNull()?.let(::year)
            if (year != null) words = words.drop(1)
            // The longest list name wins ("KISA SINAV 2" over "KISA SINAV" + 2, "YAZ OKULU VİZE" over "VİZE").
            val candidates = ExamNameCatalog.entries.sortedByDescending { it.name.length }
            for (entry in candidates) {
                val nameWords = entry.name.split(' ')
                if (words.size < nameWords.size || words.subList(0, nameWords.size) != nameWords) continue
                val rest = words.drop(nameWords.size)
                if (rest.isEmpty()) return ExamListTitle(year, entry.id, null)
                val number = if (rest.size == 1) sequenceNumber(rest[0]) else null
                if (number == null || entry.id.startsWith("kisa-sinav-")) continue
                return ExamListTitle(year, entry.id, number)
            }
            return null
        }

        /** A 19xx/20xx year word. */
        fun year(word: String): Int? =
            word.takeIf { w -> w.length == 4 && (w.startsWith("19") || w.startsWith("20")) && w.all { it in '0'..'9' } }
                ?.toInt()

        /** `1`…`99` without a leading zero. */
        fun sequenceNumber(word: String): Int? =
            word.takeIf { w -> w.length in 1..2 && w.all { it in '0'..'9' } && !w.startsWith("0") }?.toInt()
    }
}
