package com.salticusteam.vifi.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.School
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ExamKind

// Presentation of archive levels and exam kinds (iOS LevelStyle.swift).

/** Title of a list of entries of this level ("Fakülteler"). */
@get:StringRes
val ArchiveLevel.listTitleRes: Int
    get() = when (this) {
        ArchiveLevel.UNIVERSITY -> R.string.archive_level_university_list_title
        ArchiveLevel.FACULTY -> R.string.archive_level_faculty_list_title
        ArchiveLevel.DEPARTMENT -> R.string.archive_level_department_list_title
        ArchiveLevel.LESSON -> R.string.archive_level_lesson_list_title
        ArchiveLevel.EXAM -> R.string.archive_level_exam_list_title
    }

val ArchiveLevel.icon: ImageVector
    get() = when (this) {
        ArchiveLevel.UNIVERSITY -> Icons.Filled.AccountBalance
        ArchiveLevel.FACULTY -> Icons.Filled.School
        ArchiveLevel.DEPARTMENT -> Icons.AutoMirrored.Filled.LibraryBooks
        ArchiveLevel.LESSON -> Icons.AutoMirrored.Filled.MenuBook
        ArchiveLevel.EXAM -> Icons.Filled.Description
    }

/** "5 fakülte" — the number of children of an entry of this level; empty for exams. */
@Composable
fun ArchiveLevel.childCountText(count: Int): String = when (this) {
    ArchiveLevel.UNIVERSITY -> pluralStringResource(R.plurals.archive_university_child_count, count, count)
    ArchiveLevel.FACULTY -> pluralStringResource(R.plurals.archive_faculty_child_count, count, count)
    ArchiveLevel.DEPARTMENT -> pluralStringResource(R.plurals.archive_department_child_count, count, count)
    ArchiveLevel.LESSON -> pluralStringResource(R.plurals.archive_lesson_child_count, count, count)
    ArchiveLevel.EXAM -> ""
}

val ExamKind.icon: ImageVector
    get() = when (this) {
        ExamKind.IMAGES -> Icons.Filled.PhotoLibrary
        ExamKind.PDF -> Icons.Filled.PictureAsPdf
    }

/** Short format label shown as a badge ("Görsel" / "PDF"). */
@get:StringRes
val ExamKind.badgeRes: Int
    get() = when (this) {
        ExamKind.IMAGES -> R.string.exam_kind_images_badge
        ExamKind.PDF -> R.string.exam_kind_pdf_badge
    }

/** "4 sayfa" for images, "PDF belge" for documents. */
@Composable
fun ExamKind.contentText(fileCount: Int): String = when (this) {
    ExamKind.IMAGES -> pluralStringResource(R.plurals.exam_kind_images_page_count, fileCount, fileCount)
    ExamKind.PDF -> stringResource(R.string.exam_kind_pdf_content)
}
