package com.salticusteam.vifi.core.util

import java.util.regex.Pattern

// Added in 4.2.0 — a question label as the printed number alone (iOS `QuestionLabel`, server `normalizeQuestionLabel`).
/**
 * "Soru 1)", "S.1", "1." and "(1)" become "1"; "Soru 2a)", "2 a" and "2-a" become "2a". Cards are titled
 * "<label>. Soru", so a copied "Soru 1)" would show "Soru 1). Soru". A label without a number ("Ek Soru") stays as it
 * is (trimmed). Same pattern and cases as the server; pure and idempotent.
 */
object QuestionLabel {
    private val PATTERN: Pattern = Pattern.compile(
        """^[(\[]?\s*(?:(?:soru|s)\s*(?:no|numarası)?\s*[:.\-]?\s*)?(\d+)\s*[.\-)]?\s*([a-zçğıöşü])?\s*[)\].:\-]*$""",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
    )

    fun normalize(label: String): String {
        val trimmed = label.trim()
        val matcher = PATTERN.matcher(trimmed)
        if (!matcher.matches()) return trimmed
        return matcher.group(1) + (matcher.group(2) ?: "")
    }
}
