package com.salticusteam.vifi.feature.moderation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSubmissionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val submissionDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.forLanguageTag("tr-TR")).withZone(ZoneId.systemDefault())

/** "12 Eki 2026 14:30" (iOS `.abbreviated` date + `.shortened` time). Built once; formatters are thread-safe. */
fun Instant.toSubmissionDate(): String = submissionDateFormatter.format(this)

/** The kind's Turkish label (iOS `ExamKind.label`). */
@get:StringRes
val ExamKind.moderationLabelRes: Int
    get() = when (this) {
        ExamKind.IMAGES -> R.string.moderation_kind_images
        ExamKind.PDF -> R.string.moderation_kind_pdf
    }

/** "2 görsel" / "1 PDF". */
@Composable
fun fileCountText(record: ExamSubmissionRecord): String {
    val count = record.files.size
    val id = when (record.kind) {
        ExamKind.IMAGES -> R.plurals.moderation_file_count_images
        ExamKind.PDF -> R.plurals.moderation_file_count_pdf
    }
    return pluralStringResource(id, count, count)
}
