package com.salticusteam.vifi.feature.conversionadmin

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ExamConversion
import java.util.Locale

private val TURKISH: Locale = Locale.forLanguageTag("tr-TR")

/** `$0,42`: US dollars with two decimals and the Turkish decimal comma (iOS `Double.usd`). */
fun Double.toUsd(): String = String.format(TURKISH, "\$%.2f", this)

/** The provider's name (iOS `AIProvider.displayName`). */
@get:StringRes
val AIProvider.nameRes: Int
    get() = when (this) {
        AIProvider.CLAUDE -> R.string.conversion_admin_provider_claude
        AIProvider.GEMINI -> R.string.conversion_admin_provider_gemini
    }

/** The exam's name for a list row, e.g. "2023 VİZE" (iOS `ExamConversion.examTitle`); the job id when unknown. */
val ExamConversion.examTitle: String get() = placementNames.lastOrNull() ?: id

/** "Üniversite › Fakülte › Bölüm › Ders", without the exam itself (iOS `placementPath`). */
val ExamConversion.placementPath: String get() = placementNames.dropLast(1).joinToString(" › ")

/** The job's cost as dollars, `null` when nothing is known yet. */
val ExamConversion.costText: String? get() = usage?.costUsd?.toUsd()

/** "Claude · $0,42", "Gemini", or empty when nothing is known yet (iOS `providerAndCost`). */
@Composable
fun ExamConversion.providerAndCost(): String =
    listOfNotNull(provider?.let { stringResource(it.nameRes) }, costText).joinToString(" · ")

/** The status line of a queued conversion, with the provider in brackets for the running states. */
@Composable
fun ConversionStatus.statusText(provider: AIProvider?): String {
    val name = provider?.let { stringResource(it.nameRes) }
    return when (this) {
        ConversionStatus.QUEUED -> stringResource(R.string.conversion_admin_status_queued) + providerSuffix(name)
        ConversionStatus.PROCESSING -> stringResource(R.string.conversion_admin_status_processing) + providerSuffix(name)
        ConversionStatus.READY -> stringResource(R.string.conversion_admin_status_ready)
        ConversionStatus.FAILED -> stringResource(R.string.conversion_admin_status_failed)
        ConversionStatus.PUBLISHED -> stringResource(R.string.conversion_admin_status_published)
        ConversionStatus.DISCARDED -> stringResource(R.string.conversion_admin_status_discarded)
    }
}

private fun providerSuffix(name: String?): String = name?.let { " ($it)" }.orEmpty()
