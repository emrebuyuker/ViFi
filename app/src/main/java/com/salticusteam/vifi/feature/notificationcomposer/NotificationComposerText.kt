package com.salticusteam.vifi.feature.notificationcomposer

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Turkish labels of the admin notification screens (iOS `NotificationAdminLabels.swift` and the composer's
// `Issue.message`), and their date format.

private val composerFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.forLanguageTag("tr-TR")).withZone(ZoneId.systemDefault())

/** "30 Eki 2026 14:30" (iOS abbreviated date, short time) in the device's time zone. Built once; thread-safe. */
fun Instant.toComposerDateTime(): String = composerFormatter.format(this)

/** The kinds an admin can pick; `system` is for automatic messages only. */
val SELECTABLE_KINDS = listOf(NotificationKind.GENERAL, NotificationKind.PROMO)

/** "Duyuru" / "Kampanya" / "Sistem". */
@get:StringRes
val NotificationKind.adminLabelRes: Int
    get() = when (this) {
        NotificationKind.GENERAL -> R.string.notification_composer_kind_general
        NotificationKind.PROMO -> R.string.notification_composer_kind_promo
        NotificationKind.SYSTEM -> R.string.notification_composer_kind_system
    }

@get:StringRes
val ActionChoice.labelRes: Int
    get() = when (this) {
        ActionChoice.NONE -> R.string.notification_composer_action_none
        ActionChoice.PAYWALL -> R.string.notification_composer_action_paywall
        ActionChoice.ADD_EXAM -> R.string.notification_composer_action_add_exam
        ActionChoice.URL -> R.string.notification_composer_action_url
        ActionChoice.EXAM -> R.string.notification_composer_action_exam
        ActionChoice.LOCATION -> R.string.notification_composer_action_location
    }

/** The gender filter's segments: both (`null`), women, men. */
@StringRes
fun genderLabelRes(gender: Gender?): Int = when (gender) {
    null -> R.string.notification_composer_gender_all
    Gender.FEMALE -> R.string.notification_composer_gender_female
    Gender.MALE -> R.string.notification_composer_gender_male
}

/** Why the draft cannot be sent yet. */
@Composable
fun NotificationDraft.Issue.message(): String = when (this) {
    NotificationDraft.Issue.EMPTY_TITLE -> stringResource(R.string.notification_composer_issue_empty_title)
    NotificationDraft.Issue.TITLE_TOO_LONG ->
        pluralStringResource(
            R.plurals.notification_composer_issue_title_too_long,
            NotificationDraft.TITLE_LIMIT,
            NotificationDraft.TITLE_LIMIT,
        )
    NotificationDraft.Issue.EMPTY_BODY -> stringResource(R.string.notification_composer_issue_empty_body)
    NotificationDraft.Issue.BODY_TOO_LONG ->
        pluralStringResource(
            R.plurals.notification_composer_issue_body_too_long,
            NotificationDraft.BODY_LIMIT,
            NotificationDraft.BODY_LIMIT,
        )
    NotificationDraft.Issue.INVALID_AUDIENCE -> stringResource(R.string.notification_composer_issue_invalid_audience)
    NotificationDraft.Issue.INVALID_LOCATION -> stringResource(R.string.notification_composer_issue_invalid_location)
    NotificationDraft.Issue.INVALID_URL -> stringResource(R.string.notification_composer_issue_invalid_url)
    NotificationDraft.Issue.EMPTY_EXAM -> stringResource(R.string.notification_composer_issue_empty_exam)
    NotificationDraft.Issue.INVALID_IMAGE -> stringResource(R.string.notification_composer_issue_invalid_image)
    NotificationDraft.Issue.SCHEDULE_IN_PAST -> stringResource(R.string.notification_composer_issue_schedule_in_past)
    NotificationDraft.Issue.EXPIRY_BEFORE_DELIVERY ->
        stringResource(R.string.notification_composer_issue_expiry_before_delivery)
}

@Composable
fun ComposerError.message(): String = when (this) {
    is ComposerError.Message -> text
    ComposerError.SendFailed -> stringResource(R.string.notification_composer_error_send_failed)
}

@Composable
fun ComposerSuccess.message(): String = when (this) {
    is ComposerSuccess.Sent -> stringResource(R.string.notification_composer_success_sent, recipients)
    is ComposerSuccess.Scheduled -> at?.let {
        stringResource(R.string.notification_composer_success_scheduled_at, it.toComposerDateTime())
    } ?: stringResource(R.string.notification_composer_success_scheduled)
    ComposerSuccess.TestSent -> stringResource(R.string.notification_composer_success_test)
}
