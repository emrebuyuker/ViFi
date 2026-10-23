package com.salticusteam.vifi.core.model

// Added in 2.5 — a Turkish mobile number (`+90 5XX XXX XX XX`), the only kind ViFi signs in with.
/** Pure parsing and formatting, independent of Firebase, so the login screen's input handling is testable. */
class PhoneNumber private constructor(
    /** Ten digits starting with `5`, e.g. `5321234567`. */
    val nationalNumber: String,
) {
    /** `+905321234567`, the format phone verification expects. */
    val e164: String get() = COUNTRY_CODE + nationalNumber

    /** `+90 532 123 45 67`, for display. */
    val formatted: String get() = "$COUNTRY_CODE ${formatNational(nationalNumber)}"

    override fun equals(other: Any?): Boolean = other is PhoneNumber && other.nationalNumber == nationalNumber

    override fun hashCode(): Int = nationalNumber.hashCode()

    override fun toString(): String = "PhoneNumber($e164)"

    companion object {
        /** The country calling code shown as a fixed prefix in front of the input. */
        const val COUNTRY_CODE = "+90"

        /** Digits of a national mobile number, without the trunk prefix `0`. */
        const val NATIONAL_LENGTH = 10

        /** `null` unless [nationalNumber] is exactly ten ASCII digits starting with `5`. */
        fun fromNational(nationalNumber: String): PhoneNumber? {
            if (nationalNumber.length != NATIONAL_LENGTH) return null
            if (nationalNumber.first() != '5') return null
            if (!nationalNumber.all(::isAsciiDigit)) return null
            return PhoneNumber(nationalNumber)
        }

        /** Parses whatever the user typed or pasted: `532 123 45 67`, `0532-123-45-67`, `+90 532 123 45 67`... */
        fun fromInput(input: String): PhoneNumber? = fromNational(nationalDigits(input))

        /** Parses an E.164 number as Firebase reports it (`+905321234567`); `null` for other countries. */
        fun fromE164(e164: String): PhoneNumber? {
            if (!e164.startsWith(COUNTRY_CODE)) return null
            return fromNational(e164.removePrefix(COUNTRY_CODE))
        }

        /**
         * The national digits in [input] (at most ten), with any country code or trunk prefix removed.
         *
         * Accepts the formats people paste (`+90 5...`, `0090 5...`, `90 5...`, `05...`) with spaces, dashes,
         * dots or parentheses. A partial number keeps its digits so it can be completed.
         */
        fun nationalDigits(input: String): String {
            val trimmed = input.trim()
            var digits = trimmed.filter(::isAsciiDigit)

            if (digits.startsWith("0090")) {
                digits = digits.drop(4)
            } else if (digits.startsWith("90") && (trimmed.startsWith("+") || digits.length > NATIONAL_LENGTH)) {
                // National mobile numbers start with 5, so a leading 90 is the country code.
                digits = digits.drop(2)
            }
            return digits.trimStart('0').take(NATIONAL_LENGTH)
        }

        /** Groups up to ten national digits as `5XX XXX XX XX`; partial input is grouped as far as it goes. */
        fun formatNational(digits: String): String {
            val groups = mutableListOf<String>()
            var remaining = digits.take(NATIONAL_LENGTH)
            for (size in listOf(3, 3, 2, 2)) {
                if (remaining.isEmpty()) break
                groups += remaining.take(size)
                remaining = remaining.drop(size)
            }
            return groups.joinToString(" ")
        }

        /** The user's input reformatted as `5XX XXX XX XX` (live formatting of the text field). */
        fun formattedInput(input: String): String = formatNational(nationalDigits(input))

        /** `0`-`9` only; `isDigit` would also accept other scripts' digits. */
        private fun isAsciiDigit(c: Char): Boolean = c in '0'..'9'
    }
}

// Added in 2.5 — verification codes sent by SMS.
object VerificationCode {
    /** Firebase phone verification codes are six digits. */
    const val LENGTH = 6

    /** The ASCII digits of [input], at most [LENGTH] of them (autofilled or pasted codes may contain spaces). */
    fun digits(input: String): String = input.filter { it in '0'..'9' }.take(LENGTH)
}
