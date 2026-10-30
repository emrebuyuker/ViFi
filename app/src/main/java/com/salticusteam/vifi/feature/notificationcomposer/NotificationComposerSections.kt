package com.salticusteam.vifi.feature.notificationcomposer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.AudienceNode
import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationLocation
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

// The composer form, split into small sections so each stays readable (iOS NotificationComposerSections.swift).
// Every control is disabled while a request is in flight.

internal const val COMPOSER_TAG = "notificationComposer"

// --- Kind ---

/**
 * Duyuru / Kampanya, with the one thing an admin must know about campaigns. While the action is Konum the control
 * is locked on Kampanya (a place is always advertising) and says why; the admin's own choice returns afterwards.
 */
@Composable
internal fun KindSection(state: NotificationComposerUiState, onKindSelected: (NotificationKind) -> Unit) {
    ComposerSection(
        header = null,
        footer = {
            if (state.isKindLocked) {
                Row(
                    modifier = Modifier.testTag("$COMPOSER_TAG.kind.lockNote"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    FooterText(stringResource(R.string.notification_composer_kind_lock_note))
                }
            } else {
                FooterText(stringResource(R.string.notification_composer_kind_footer))
            }
        },
    ) {
        ComposerSegmented(
            label = stringResource(R.string.notification_composer_kind_label),
            options = SELECTABLE_KINDS,
            selected = state.effectiveKind,
            optionLabel = { stringResource(it.adminLabelRes) },
            optionTag = { "$COMPOSER_TAG.kind.${it.raw}" },
            onSelect = onKindSelected,
            enabled = !state.isWorking && !state.isKindLocked,
            modifier = Modifier.testTag("$COMPOSER_TAG.kind"),
        )
    }
}

// --- Content ---

/** Title and message, each with a live counter against its limit. */
@Composable
internal fun ContentSection(
    state: NotificationComposerUiState,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
) {
    ComposerSection(header = stringResource(R.string.notification_composer_content_header)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.notification_composer_title_field)) },
                singleLine = true,
                enabled = !state.isWorking,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth().testTag("$COMPOSER_TAG.title"),
            )
            CharacterCounter(state.titleLength, NotificationDraft.TITLE_LIMIT, "$COMPOSER_TAG.titleCounter")
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = state.body,
                onValueChange = onBodyChange,
                label = { Text(stringResource(R.string.notification_composer_body_field)) },
                minLines = 3,
                maxLines = 8,
                enabled = !state.isWorking,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().testTag("$COMPOSER_TAG.body"),
            )
            CharacterCounter(state.bodyLength, NotificationDraft.BODY_LIMIT, "$COMPOSER_TAG.bodyCounter")
        }
    }
}

/** "12/80", in the error colour once over the limit. */
@Composable
private fun CharacterCounter(count: Int, limit: Int, testTag: String) {
    val description = pluralStringResource(R.plurals.cd_notification_composer_counter, limit, count, limit)
    Text(
        text = stringResource(R.string.notification_composer_counter, count, limit),
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
        color = if (count > limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            .testTag(testTag),
    )
}

// --- Audience ---

/**
 * Everyone, or a segment picked through cascading university → faculty → department lists that show only what
 * registered users actually have, plus a gender filter and the live reach estimate.
 */
@Composable
internal fun AudienceSection(
    state: NotificationComposerUiState,
    onAudienceModeChange: (AudienceMode) -> Unit,
    onRetryFacets: () -> Unit,
    onUniversitySelected: (String?) -> Unit,
    onFacultySelected: (String?) -> Unit,
    onDepartmentSelected: (String?) -> Unit,
    onGenderSelected: (Gender?) -> Unit,
) {
    val enabled = !state.isWorking
    val footer = audienceFooter(state)
    ComposerSection(
        header = stringResource(R.string.notification_composer_audience_header),
        footer = footer?.let { text -> { FooterText(text) } },
    ) {
        ComposerSegmented(
            label = stringResource(R.string.notification_composer_audience_label),
            options = AudienceMode.entries,
            selected = state.audienceMode,
            optionLabel = { stringResource(it.labelRes) },
            optionTag = { "$COMPOSER_TAG.audience.${it.name.lowercase()}" },
            onSelect = onAudienceModeChange,
            enabled = enabled,
            modifier = Modifier.testTag("$COMPOSER_TAG.audience"),
        )
        FacetsStatus(state, enabled, onRetryFacets)
        if (state.audienceMode == AudienceMode.SEGMENT && state.facets != null) {
            NodeDropdown(
                label = stringResource(R.string.notification_composer_university),
                noneText = stringResource(R.string.notification_composer_select),
                nodes = state.universities,
                selectedId = state.universityId,
                onSelect = onUniversitySelected,
                enabled = enabled,
                testTag = "$COMPOSER_TAG.university",
            )
            NodeDropdown(
                label = stringResource(R.string.notification_composer_faculty),
                noneText = stringResource(R.string.notification_composer_faculty_all),
                nodes = state.faculties,
                selectedId = state.facultyId,
                onSelect = onFacultySelected,
                enabled = enabled && state.universityId != null,
                testTag = "$COMPOSER_TAG.faculty",
            )
            NodeDropdown(
                label = stringResource(R.string.notification_composer_department),
                noneText = stringResource(R.string.notification_composer_department_all),
                nodes = state.departments,
                selectedId = state.departmentId,
                onSelect = onDepartmentSelected,
                enabled = enabled && state.facultyId != null,
                testTag = "$COMPOSER_TAG.department",
            )
            ComposerSegmented(
                label = stringResource(R.string.notification_composer_gender),
                options = listOf(null, Gender.FEMALE, Gender.MALE),
                selected = state.gender,
                optionLabel = { stringResource(genderLabelRes(it)) },
                optionTag = { "$COMPOSER_TAG.gender.${it?.raw ?: "all"}" },
                onSelect = onGenderSelected,
                enabled = enabled,
                modifier = Modifier.testTag("$COMPOSER_TAG.gender"),
            )
        }
        state.estimatedRecipients?.let { estimate ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .testTag("$COMPOSER_TAG.estimate"),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.notification_composer_estimate_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = pluralStringResource(R.plurals.notification_composer_estimate_value, estimate, estimate),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
    }
}

private val AudienceMode.labelRes: Int
    get() = when (this) {
        AudienceMode.EVERYONE -> R.string.notification_composer_audience_everyone
        AudienceMode.SEGMENT -> R.string.notification_composer_audience_segment
    }

/** "Kitle bilgisi yükleniyor…", or the failure with "Tekrar dene". Nothing once the facets are in. */
@Composable
private fun FacetsStatus(state: NotificationComposerUiState, enabled: Boolean, onRetry: () -> Unit) {
    val error = state.facetsError
    when {
        state.isFacetsLoading -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.notification_composer_audience_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        error != null -> Column {
            StatusLine(icon = Icons.Filled.Error, text = error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry, enabled = enabled, modifier = Modifier.testTag("$COMPOSER_TAG.audienceRetry")) {
                Text(stringResource(R.string.notification_composer_audience_retry))
            }
        }
    }
}

@Composable
private fun audienceFooter(state: NotificationComposerUiState): String? = when {
    state.audienceMode == AudienceMode.SEGMENT && state.universityId == null && state.facets != null ->
        stringResource(R.string.notification_composer_audience_footer_segment)
    state.effectiveKind == NotificationKind.PROMO -> stringResource(R.string.notification_composer_audience_footer_promo)
    else -> null
}

/** One audience level: "none" ([noneText]) or a node, each option with how many users it reaches. */
@Composable
private fun NodeDropdown(
    label: String,
    noneText: String,
    nodes: List<AudienceNode>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    enabled: Boolean,
    testTag: String,
) {
    val selected = nodes.firstOrNull { it.id == selectedId }
    val options = listOf(DropdownOption(noneText, "$testTag.option.none") { onSelect(null) }) +
        nodes.map { node ->
            DropdownOption(
                text = pluralStringResource(
                    R.plurals.notification_composer_audience_option,
                    node.counts.total,
                    node.name,
                    node.counts.total,
                ),
                testTag = "$testTag.option.${node.id}",
            ) { onSelect(node.id) }
        }
    ComposerDropdown(label = label, value = selected?.name ?: noneText, options = options, enabled = enabled, testTag = testTag)
}

// --- Action ---

/**
 * The button on the notification's card: nothing, Premium, Sınav Ekle, an https link, an exam from the archive or a
 * place on the map. The place's rows belong to the location picker ([locationRows]); [examRow] is the exam's.
 */
@Composable
internal fun ActionSection(
    state: NotificationComposerUiState,
    onActionChoiceChange: (ActionChoice) -> Unit,
    onUrlTextChange: (String) -> Unit,
    onLocationPicked: (NotificationLocation) -> Unit,
    locationRows: ComposerLocationRows,
    examRow: @Composable () -> Unit,
) {
    val enabled = !state.isWorking
    ComposerSection(
        header = stringResource(R.string.notification_composer_action_header),
        footer = { FooterText(stringResource(R.string.notification_composer_action_footer)) },
    ) {
        ComposerDropdown(
            label = stringResource(R.string.notification_composer_action_label),
            value = stringResource(state.actionChoice.labelRes),
            options = ActionChoice.entries.map { choice ->
                DropdownOption(stringResource(choice.labelRes), "$COMPOSER_TAG.action.${choice.name.lowercase()}") {
                    onActionChoiceChange(choice)
                }
            },
            enabled = enabled,
            testTag = "$COMPOSER_TAG.action",
        )
        when (state.actionChoice) {
            ActionChoice.URL -> OutlinedTextField(
                value = state.urlText,
                onValueChange = onUrlTextChange,
                placeholder = { Text(stringResource(R.string.notification_composer_url_placeholder)) },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                ),
                modifier = Modifier.fillMaxWidth().testTag("$COMPOSER_TAG.url"),
            )
            ActionChoice.EXAM -> examRow()
            ActionChoice.LOCATION -> locationRows(state.location, enabled, onLocationPicked)
            ActionChoice.NONE, ActionChoice.PAYWALL, ActionChoice.ADD_EXAM -> Unit
        }
    }
}

// --- Timing ---

/** An optional later delivery time and an optional validity period. */
@Composable
internal fun TimingSection(
    state: NotificationComposerUiState,
    onSchedulingToggle: (Boolean) -> Unit,
    onScheduledAtChange: (Instant) -> Unit,
    onExpiryToggle: (Boolean) -> Unit,
    onExpiresAtChange: (Instant) -> Unit,
) {
    val enabled = !state.isWorking
    ComposerSection(
        header = stringResource(R.string.notification_composer_timing_header),
        footer = { FooterText(stringResource(R.string.notification_composer_timing_footer)) },
    ) {
        ToggleRow(
            label = stringResource(R.string.notification_composer_schedule_toggle),
            checked = state.scheduledAt != null,
            onCheckedChange = onSchedulingToggle,
            enabled = enabled,
            testTag = "$COMPOSER_TAG.scheduleToggle",
        )
        state.scheduledAt?.let { scheduledAt ->
            DateTimeRow(
                label = stringResource(R.string.notification_composer_scheduled_at),
                value = scheduledAt,
                earliest = state.now,
                onChange = onScheduledAtChange,
                enabled = enabled,
                testTag = "$COMPOSER_TAG.scheduledAt",
            )
        }
        ToggleRow(
            label = stringResource(R.string.notification_composer_expiry_toggle),
            checked = state.expiresAt != null,
            onCheckedChange = onExpiryToggle,
            enabled = enabled,
            testTag = "$COMPOSER_TAG.expiryToggle",
        )
        state.expiresAt?.let { expiresAt ->
            DateTimeRow(
                label = stringResource(R.string.notification_composer_expires_at),
                value = expiresAt,
                earliest = state.scheduledAt ?: state.now,
                onChange = onExpiresAtChange,
                enabled = enabled,
                testTag = "$COMPOSER_TAG.expiresAt",
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean, testTag: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier
                .semantics { contentDescription = label }
                .testTag(testTag),
        )
    }
}

/** A date and time; a tap picks the day (from [earliest] on) and then the hour and minute, in the device's zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeRow(
    label: String,
    value: Instant,
    earliest: Instant,
    onChange: (Instant) -> Unit,
    enabled: Boolean,
    testTag: String,
) {
    val zone = ZoneId.systemDefault()
    var pickedDay by remember { mutableStateOf<LocalDate?>(null) }
    var isPickingDay by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClickLabel = label) { isPickingDay = true }
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value.toComposerDateTime(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (isPickingDay) {
        val firstDay = earliest.atZone(zone).toLocalDate()
        val dayState = rememberDatePickerState(
            initialSelectedDateMillis = value.atZone(zone).toLocalDate().utcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = !utcDay(utcTimeMillis).isBefore(firstDay)

                override fun isSelectableYear(year: Int): Boolean = year >= firstDay.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { isPickingDay = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        isPickingDay = false
                        pickedDay = dayState.selectedDateMillis?.let(::utcDay)
                    },
                    modifier = Modifier.testTag("$testTag.day.confirm"),
                ) { Text(stringResource(R.string.notification_composer_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { isPickingDay = false }) {
                    Text(stringResource(R.string.notification_composer_cancel))
                }
            },
        ) {
            DatePicker(state = dayState)
        }
    }

    pickedDay?.let { day ->
        val current = value.atZone(zone).toLocalTime()
        val timeState = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickedDay = null },
            title = { Text(label) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickedDay = null
                        onChange(day.atTime(LocalTime.of(timeState.hour, timeState.minute)).atZone(zone).toInstant())
                    },
                    modifier = Modifier.testTag("$testTag.time.confirm"),
                ) { Text(stringResource(R.string.notification_composer_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickedDay = null }) {
                    Text(stringResource(R.string.notification_composer_cancel))
                }
            },
        )
    }
}

/** The Material date picker works on UTC midnights. */
private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcDay(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

// --- Feedback and send ---

/** What the admin needs to know right now: what failed, what went through, what blocks sending. */
@Composable
internal fun FeedbackSection(state: NotificationComposerUiState) {
    if (!state.hasFeedback) return
    ComposerSection(header = null) {
        state.error?.let { error ->
            StatusLine(
                icon = Icons.Filled.Error,
                text = error.message(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("$COMPOSER_TAG.error"),
            )
        }
        state.success?.let { success ->
            StatusLine(
                icon = Icons.Filled.CheckCircle,
                text = success.message(),
                color = MaterialTheme.colorScheme.onSurface,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("$COMPOSER_TAG.success"),
            )
        }
        state.visibleIssue?.let { issue ->
            StatusLine(
                icon = Icons.Outlined.Info,
                text = issue.message(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("$COMPOSER_TAG.issue"),
            )
        }
    }
}

/** "Gönder" (previews the reach first) and "Bana test gönder" (to the admin's own devices only). */
@Composable
internal fun SendSection(state: NotificationComposerUiState, onSend: () -> Unit, onTestSend: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Primary content is onPrimary (black): white on the brand orange falls below 3:1.
        Button(
            onClick = onSend,
            enabled = state.canSend,
            modifier = Modifier.fillMaxWidth().testTag("$COMPOSER_TAG.send"),
        ) {
            if (state.isWorking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.notification_composer_send), fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = onTestSend,
            enabled = state.canSendTest,
            modifier = Modifier.fillMaxWidth().testTag("$COMPOSER_TAG.testSend"),
        ) {
            Icon(Icons.Filled.Smartphone, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.notification_composer_test_send))
        }
        FooterText(stringResource(R.string.notification_composer_send_footer))
    }
}

// --- Shared controls ---

/** A titled group of the form (iOS Form section): an optional header, the controls on a card, an optional footer. */
@Composable
internal fun ComposerSection(
    header: String?,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (header != null) {
            Text(
                text = header,
                style = MaterialTheme.typography.titleSmall,
                // Secondary text like iOS section headers; the brand orange is too light for small text.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp, bottom = 6.dp)
                    .semantics { heading() },
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
        if (footer != null) {
            Column(modifier = Modifier.padding(start = 4.dp, top = 6.dp, end = 4.dp)) { footer() }
        }
    }
}

@Composable
internal fun FooterText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** An icon and a message in [color] (iOS `Label`). */
@Composable
internal fun StatusLine(
    icon: ImageVector,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    iconTint: Color = color,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** A labelled segmented control (iOS segmented `Picker`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ComposerSegmented(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    optionTag: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier.testTag(optionTag(option)),
                ) {
                    Text(optionLabel(option))
                }
            }
        }
    }
}

/** One choice of a [ComposerDropdown]. */
internal class DropdownOption(val text: String, val testTag: String, val onClick: () -> Unit)

/** A labelled menu picker (iOS menu `Picker`) showing [value]; the field itself is tagged `<testTag>.field`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposerDropdown(
    label: String,
    value: String,
    options: List<DropdownOption>,
    enabled: Boolean,
    testTag: String,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
                .testTag("$testTag.field"),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.text) },
                    onClick = {
                        option.onClick()
                        expanded = false
                    },
                    modifier = Modifier.testTag(option.testTag),
                )
            }
        }
    }
}
