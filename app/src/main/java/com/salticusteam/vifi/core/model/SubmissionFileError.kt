package com.salticusteam.vifi.core.model

// Added in 2.5 — why a picked file could not be added to a submission, with the message shown on the form.
sealed class SubmissionFileError(val userMessage: String) : Exception(userMessage) {

    /** Adding the picked images would exceed [ExamSubmission.MAX_FILE_COUNT] pages. */
    data object TooManyPages :
        SubmissionFileError("En fazla ${ExamSubmission.MAX_FILE_COUNT} sayfa ekleyebilirsin.")

    /** A picked image is not decodable or cannot be brought under the size limit; it was skipped. */
    data object ImageUnusable : SubmissionFileError("Bir görsel işlenemedi. Lütfen başka bir görsel dene.")

    data object EmptyFile : SubmissionFileError("Dosya boş görünüyor. Lütfen başka bir dosya seç.")

    data object PdfTooLarge : SubmissionFileError("PDF 15 MB'tan büyük olamaz.")

    data object Unreadable : SubmissionFileError("Dosya okunamadı. Lütfen tekrar dene.")
}
