package com.salticusteam.vifi.core.model

// Added in 2.5 — a user-contributed exam awaiting moderation, collected by the "Sınav Ekle" flow.
/**
 * Written to Firestore `submissions/<id>` and uploaded to Storage `pending/<uid>/<submissionId>/...`. Pure
 * value type, independent of Firebase, so the form, its validation and the server payload are testable. The
 * archive path is captured as the human-readable display names the server schema stores (flat denormalized
 * text, no canonical ids). The free-typed exam name and the year are kept apart here and folded into the
 * single `exam` text field at encode time. The owner's uid is not part of the model; the service takes it
 * from the verified session.
 */
data class ExamSubmission(
    /** University display name, taken from the archive catalog; 1..[MAX_TEXT_LENGTH] characters. */
    val university: String,
    val faculty: String,
    val department: String,
    /** Course (`ders`) display name: the archive's lesson level. */
    val lesson: String,
    /** The free-typed exam name, e.g. "Vize", "Final 1". */
    val examName: String,
    /** The exam year, chosen from a four-digit picker; folded into the stored `exam` text. */
    val year: Int,
    val kind: ExamKind,
    /** The files to upload, in reading order. */
    val files: List<SubmissionFile>,
) {
    /**
     * The first invariant this submission violates, or `null` when it is ready to send. Mirrors the server
     * contract (five non-empty text fields <=120 chars, 1..50 files, a single PDF or many images, each file
     * 1..15 MiB), so the service can refuse an inconsistent submission before touching the network.
     */
    val validationError: ExamSubmissionValidationError?
        get() {
            val fields = listOf(university, faculty, department, lesson, examText(examName, year))
            if (examName.isEmpty() || !fields.all(::isValidText)) return ExamSubmissionValidationError.InvalidText
            if (year !in YEAR_RANGE) return ExamSubmissionValidationError.InvalidYear
            if (files.isEmpty()) return ExamSubmissionValidationError.NoFiles
            if (files.size > MAX_FILE_COUNT) return ExamSubmissionValidationError.TooManyFiles
            if (kind == ExamKind.PDF && files.size != 1) return ExamSubmissionValidationError.PdfMustBeSingleFile
            for (file in files) {
                if (file.data.isEmpty()) return ExamSubmissionValidationError.EmptyFile(file.order)
                if (file.data.size > MAX_FILE_BYTES) return ExamSubmissionValidationError.FileTooLarge(file.order)
            }
            return null
        }

    /** Whether every server invariant is satisfied. */
    val isValid: Boolean get() = validationError == null

    companion object {
        /** Longest accepted value of any single taxonomy/exam text field (matches the Firestore rule's 120-char cap). */
        const val MAX_TEXT_LENGTH = 120

        /** Most files a single submission may carry (matches the Firestore rule `files.size() <= 50`). */
        const val MAX_FILE_COUNT = 50

        /** Largest accepted size of one uploaded file, in bytes (matches the Storage rule's 15 MiB cap). */
        const val MAX_FILE_BYTES = 15 * 1024 * 1024

        /** Range of accepted exam years. */
        val YEAR_RANGE: IntRange = 1980..2100

        /**
         * The single `exam` text the server stores: the free-typed name with the year folded in (the schema has
         * no separate year field). Year-prefixed so an admin list sorts chronologically, e.g. "2023 Vize".
         */
        fun examText(name: String, year: Int): String = "$year $name"

        /** Whether [value] is an acceptable text field: non-empty and within the length cap (counted in code points). */
        fun isValidText(value: String): Boolean =
            value.isNotEmpty() && value.codePointCount(0, value.length) <= MAX_TEXT_LENGTH
    }
}

// Added in 2.5 — one file of a submission: its raw bytes and its position in the exam.
/** Equality compares the bytes by content. */
class SubmissionFile(
    val data: ByteArray,
    /** 1-based position in reading order. */
    val order: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is SubmissionFile && other.order == order && other.data.contentEquals(data)

    override fun hashCode(): Int = 31 * order + data.contentHashCode()

    override fun toString(): String = "SubmissionFile(order=$order, bytes=${data.size})"
}

// Added in 2.5 — why a submission is not ready to send (the server contract mirrored client-side).
sealed interface ExamSubmissionValidationError {
    /** A required text field is empty or longer than [ExamSubmission.MAX_TEXT_LENGTH]. */
    data object InvalidText : ExamSubmissionValidationError

    data object InvalidYear : ExamSubmissionValidationError

    data object NoFiles : ExamSubmissionValidationError

    data object TooManyFiles : ExamSubmissionValidationError

    /** A PDF submission must carry exactly one file. */
    data object PdfMustBeSingleFile : ExamSubmissionValidationError

    data class EmptyFile(val order: Int) : ExamSubmissionValidationError

    data class FileTooLarge(val order: Int) : ExamSubmissionValidationError
}

/** The HTTP content type the Storage rules require for an upload of this kind. */
val ExamKind.uploadContentType: String
    get() = when (this) {
        ExamKind.IMAGES -> "image/jpeg"
        ExamKind.PDF -> "application/pdf"
    }

/** The file-name extension used when naming an uploaded file. */
val ExamKind.fileExtension: String
    get() = when (this) {
        ExamKind.IMAGES -> "jpg"
        ExamKind.PDF -> "pdf"
    }
