package com.salticusteam.vifi.core.model

import java.text.Normalizer
import java.util.Locale

// Models of the admin subject-mapping screen (4.2.0 practice mode, owner decision P5; iOS `SubjectMapping.swift`): the
// country-wide course identities (`subjects/<id>`) and the courses mapped to each. Server contract: `listSubjects` in
// `firebase/functions/conversions/subjectsAdmin.js`.

// Added in 4.2.0 — one course catalog node that belongs to a subject, with the names that place it in the archive.
data class SubjectCourse(
    val id: String,
    val name: String,
    val universityId: String,
    val universityName: String,
    val facultyName: String,
    val departmentName: String,
    val questionCount: Int,
) {
    /** "Fakülte · Bölüm", skipping whichever part is empty. */
    val place: String get() = listOf(facultyName, departmentName).filter { it.isNotEmpty() }.joinToString(" · ")
}

// Added in 4.2.0 — a country-wide course identity and the courses currently mapped to it.
data class AdminSubject(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val courses: List<SubjectCourse>,
) {
    val courseCount: Int get() = courses.size
    val questionCount: Int get() = courses.sumOf { it.questionCount }
    val universityIds: Set<String> get() = courses.map { it.universityId }.toSet()
}

// Added in 4.2.0 — where a course goes in `setCourseSubject`: an existing subject, or a new one created from a name.
sealed interface SubjectTarget {
    data class Existing(val id: String) : SubjectTarget
    data class New(val name: String) : SubjectTarget
}

// Added in 4.2.0 — finds subjects that probably are the same course under two names ("Olası eşleşme"; iOS parity).
object SubjectNameSimilarity {
    /** Names closer than this edit-distance ratio count as the same course. */
    private const val MINIMUM_RATIO = 0.85

    /** A shorter name that is the start of a longer one counts when it has at least this many letters. */
    private const val MINIMUM_PREFIX_LENGTH = 6

    /** Names shorter than this are only ever equal, never "close". */
    private const val MINIMUM_FUZZY_LENGTH = 5
    private const val BUCKET_LENGTH = 3
    private val ROMAN_NUMERALS = mapOf("i" to "1", "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /**
     * A subject name reduced to its comparable parts: letters and digits only (Turkish letters folded), plus the
     * trailing course number with roman numerals as digits ("Fizik II" becomes `fizik` + `2`).
     */
    data class Key(val base: String, val number: String)

    /** Lowercase, without diacritics, every Turkish i ("ı", "İ", "I") as plain "i", so "CIZGE" finds "Çizge". */
    fun fold(text: String): String {
        val dotless = text.replace('ı', 'i').replace('İ', 'i').replace('I', 'i')
        return COMBINING_MARKS.replace(Normalizer.normalize(dotless, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
    }

    fun key(name: String): Key {
        val tokens = fold(name).split { !it.isLetterOrDigit() }.toMutableList()
        var number = ""
        val last = tokens.lastOrNull()
        if (last != null && tokens.size > 1) {
            val roman = ROMAN_NUMERALS[last]
            if (roman != null) {
                number = roman
                tokens.removeAt(tokens.lastIndex)
            } else if (last.all { it.isDigit() }) {
                number = last
                tokens.removeAt(tokens.lastIndex)
            }
        }
        return Key(base = tokens.joinToString(""), number = number)
    }

    /** Whether two names look like the same course: same number, and equal, prefixed or a small edit distance apart. */
    fun areClose(lhs: String, rhs: String): Boolean = areClose(key(lhs), key(rhs))

    fun areClose(lhs: Key, rhs: Key): Boolean {
        if (lhs.base.isEmpty() || rhs.base.isEmpty() || lhs.number != rhs.number) return false
        if (lhs.base == rhs.base) return true
        val (short, long) = if (lhs.base.length <= rhs.base.length) lhs.base to rhs.base else rhs.base to lhs.base
        if (short.length >= MINIMUM_PREFIX_LENGTH && long.startsWith(short)) return true
        if (short.length < MINIMUM_FUZZY_LENGTH) return false
        val distance = editDistance(lhs.base, rhs.base)
        return 1 - distance.toDouble() / long.length >= MINIMUM_RATIO
    }

    /**
     * For every subject with courses from only one university: the id of the closest-named other subject (the one
     * with most courses wins, then the smaller id). Names are compared inside buckets of the same first letters.
     */
    fun possibleMatches(subjects: List<AdminSubject>): Map<String, String> {
        val keys = LinkedHashMap<String, Key>()
        subjects.forEach { keys.putIfAbsent(it.id, key(it.name)) }
        val buckets = subjects.groupBy { keys[it.id]?.base.orEmpty().take(BUCKET_LENGTH) }
        val matches = mutableMapOf<String, String>()
        for (subject in subjects) {
            if (subject.universityIds.size != 1) continue
            val ownKey = keys[subject.id] ?: continue
            val bucket = buckets[ownKey.base.take(BUCKET_LENGTH)].orEmpty()
            val best = bucket
                .filter { it.id != subject.id && areClose(ownKey, keys[it.id] ?: ownKey) }
                .minWithOrNull(compareByDescending<AdminSubject> { it.courseCount }.thenBy { it.id })
            if (best != null) matches[subject.id] = best.id
        }
        return matches
    }

    private fun String.split(isSeparator: (Char) -> Boolean): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        for (char in this) {
            if (isSeparator(char)) {
                if (current.isNotEmpty()) parts += current.toString()
                current.clear()
            } else {
                current.append(char)
            }
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts
    }

    private fun editDistance(lhs: String, rhs: String): Int {
        if (lhs.isEmpty()) return rhs.length
        if (rhs.isEmpty()) return lhs.length
        var previous = IntArray(rhs.length + 1) { it }
        for ((row, left) in lhs.withIndex()) {
            val current = IntArray(rhs.length + 1)
            current[0] = row + 1
            for ((column, right) in rhs.withIndex()) {
                current[column + 1] = minOf(
                    previous[column + 1] + 1,
                    current[column] + 1,
                    previous[column] + if (left == right) 0 else 1,
                )
            }
            previous = current
        }
        return previous[rhs.length]
    }
}
