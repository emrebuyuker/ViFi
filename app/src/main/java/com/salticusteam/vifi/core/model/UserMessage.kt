package com.salticusteam.vifi.core.model

/**
 * The user-facing Turkish message for any error thrown by the data layer (iOS `Error.userMessage` parity).
 * Domain errors carry their own text; anything else falls back to the generic archive message so a raw
 * (English, technical) exception message never reaches the UI.
 */
val Throwable.userMessage: String
    get() = when (this) {
        is ArchiveError -> userMessage
        is AuthError -> userMessage
        is ProfileError -> userMessage
        is ExamSubmissionError -> userMessage
        is ModerationError -> userMessage
        is ExamUnlockError -> userMessage
        is ExamFileError -> userMessage
        else -> GENERIC_ERROR_MESSAGE
    }

/** Shown when an error has no domain-specific message (iOS generic copy, verbatim). */
const val GENERIC_ERROR_MESSAGE = "Bir sorun oluştu. Lütfen tekrar dene."
