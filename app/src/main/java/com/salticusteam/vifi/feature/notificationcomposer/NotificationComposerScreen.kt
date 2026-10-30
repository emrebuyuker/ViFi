package com.salticusteam.vifi.feature.notificationcomposer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationAdminService
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationLocation
import com.salticusteam.vifi.core.model.NotificationPreview
import com.salticusteam.vifi.core.navigation.RegisterModalPresence
import java.time.Instant

/**
 * The rows under "Eylem" while the action is Konum: the place chosen so far, a button opening the map picker
 * (`LocalLocationPicker`) and the campaign note. They belong to the location picker's feature (MAP-03, wired in
 * NIT-01); the composer hands them the current place, whether the form is editable, and where a picked place goes.
 */
typealias ComposerLocationRows = @Composable (
    location: NotificationLocation?,
    enabled: Boolean,
    onLocationPicked: (NotificationLocation) -> Unit,
) -> Unit

/** What the form's controls do; the screen wires them to the view model, previews and tests to what they need. */
class NotificationComposerActions(
    val onKindSelected: (NotificationKind) -> Unit = {},
    val onTitleChange: (String) -> Unit = {},
    val onBodyChange: (String) -> Unit = {},
    val onAudienceModeChange: (AudienceMode) -> Unit = {},
    val onRetryFacets: () -> Unit = {},
    val onUniversitySelected: (String?) -> Unit = {},
    val onFacultySelected: (String?) -> Unit = {},
    val onDepartmentSelected: (String?) -> Unit = {},
    val onGenderSelected: (Gender?) -> Unit = {},
    val onActionChoiceChange: (ActionChoice) -> Unit = {},
    val onUrlTextChange: (String) -> Unit = {},
    val onLocationPicked: (NotificationLocation) -> Unit = {},
    val onSchedulingToggle: (Boolean) -> Unit = {},
    val onScheduledAtChange: (Instant) -> Unit = {},
    val onExpiryToggle: (Boolean) -> Unit = {},
    val onExpiresAtChange: (Instant) -> Unit = {},
    val onSend: () -> Unit = {},
    val onTestSend: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onCancelConfirmation: () -> Unit = {},
)

/**
 * The admin "Bildirim Gönder" form (iOS `NotificationComposerView`): kind, title and message, audience, action, image
 * and timing, then a preview of the exact reach and a confirmation before anything is sent. Reachable only from the
 * account screen, by an admin; [onOpenHistory] opens the sending history. [locationRows] are the Konum action's rows.
 */
@Composable
fun NotificationComposerScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    locationRows: ComposerLocationRows = { _, _, _ -> },
    viewModel: NotificationComposerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onScreenShown() }

    val actions = remember(viewModel) {
        NotificationComposerActions(
            onKindSelected = viewModel::onKindSelected,
            onTitleChange = viewModel::onTitleChange,
            onBodyChange = viewModel::onBodyChange,
            onAudienceModeChange = viewModel::onAudienceModeChange,
            onRetryFacets = viewModel::reloadFacets,
            onUniversitySelected = viewModel::onUniversitySelected,
            onFacultySelected = viewModel::onFacultySelected,
            onDepartmentSelected = viewModel::onDepartmentSelected,
            onGenderSelected = viewModel::onGenderSelected,
            onActionChoiceChange = viewModel::onActionChoiceChange,
            onUrlTextChange = viewModel::onUrlTextChange,
            onLocationPicked = viewModel::onLocationPicked,
            onSchedulingToggle = viewModel::onSchedulingToggle,
            onScheduledAtChange = viewModel::onScheduledAtChange,
            onExpiryToggle = viewModel::onExpiryToggle,
            onExpiresAtChange = viewModel::onExpiresAtChange,
            onSend = viewModel::requestSend,
            onTestSend = viewModel::sendTest,
            onConfirm = viewModel::confirmSend,
            onCancelConfirmation = viewModel::cancelConfirmation,
        )
    }

    NotificationComposerContent(
        state = state,
        actions = actions,
        onBack = onBack,
        onOpenHistory = onOpenHistory,
        locationRows = locationRows,
    )
}

/** Stateless form; previews and UI tests drive this. */
@Composable
fun NotificationComposerContent(
    state: NotificationComposerUiState,
    actions: NotificationComposerActions,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    locationRows: ComposerLocationRows = { _, _, _ -> },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ViFiTopAppBar(
                title = stringResource(R.string.notification_composer_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenHistory, modifier = Modifier.testTag("$COMPOSER_TAG.history")) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = stringResource(R.string.notification_composer_history),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
                .testTag("$COMPOSER_TAG.form"),
        ) {
            KindSection(state, actions.onKindSelected)
            ContentSection(state, actions.onTitleChange, actions.onBodyChange)
            AudienceSection(
                state = state,
                onAudienceModeChange = actions.onAudienceModeChange,
                onRetryFacets = actions.onRetryFacets,
                onUniversitySelected = actions.onUniversitySelected,
                onFacultySelected = actions.onFacultySelected,
                onDepartmentSelected = actions.onDepartmentSelected,
                onGenderSelected = actions.onGenderSelected,
            )
            ActionSection(
                state = state,
                onActionChoiceChange = actions.onActionChoiceChange,
                onUrlTextChange = actions.onUrlTextChange,
                onLocationPicked = actions.onLocationPicked,
                locationRows = locationRows,
                examRow = {},
            )
            TimingSection(
                state = state,
                onSchedulingToggle = actions.onSchedulingToggle,
                onScheduledAtChange = actions.onScheduledAtChange,
                onExpiryToggle = actions.onExpiryToggle,
                onExpiresAtChange = actions.onExpiresAtChange,
            )
            FeedbackSection(state)
            SendSection(state, onSend = actions.onSend, onTestSend = actions.onTestSend)
        }
    }

    val confirmation = state.confirmation
    if (state.isConfirming && confirmation != null) {
        SendConfirmationDialog(confirmation, onConfirm = actions.onConfirm, onCancel = actions.onCancelConfirmation)
    }
}

/** "Bildirim gönderilsin mi?" with the exact reach, so the admin sees who this really goes to before agreeing. */
@Composable
private fun SendConfirmationDialog(
    confirmation: NotificationSendConfirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheduledAt = confirmation.draft.scheduledAt
    val preview = confirmation.preview
    val reach = pluralStringResource(
        R.plurals.notification_composer_confirm_message,
        preview.recipients,
        preview.recipients,
        preview.pushTargets,
    )
    val message = scheduledAt?.let {
        reach + "\n" + stringResource(R.string.notification_composer_confirm_scheduled_at, it.toComposerDateTime())
    } ?: reach
    val title = if (scheduledAt == null) {
        R.string.notification_composer_confirm_send_title
    } else {
        R.string.notification_composer_confirm_schedule_title
    }
    val confirmLabel = if (scheduledAt == null) {
        R.string.notification_composer_confirm_send
    } else {
        R.string.notification_composer_confirm_schedule
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(title)) },
        text = {
            // Counted while it is up, so a tapped push waits for the admin's answer.
            RegisterModalPresence()
            Text(message)
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("$COMPOSER_TAG.confirm")) {
                Text(stringResource(confirmLabel))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("$COMPOSER_TAG.confirm.cancel")) {
                Text(stringResource(R.string.notification_composer_cancel))
            }
        },
    )
}

@ViFiThemePreviews
@Composable
private fun NotificationComposerContentPreview() {
    ViFiTheme {
        NotificationComposerContent(
            state = NotificationComposerUiState(
                title = "Kampüs kafede %20 indirim",
                body = "Öğrenci kartını göster, tüm içeceklerde %20 indirim kazan.",
                kind = NotificationKind.PROMO,
                audienceMode = AudienceMode.SEGMENT,
                universityId = "bogazici-universitesi",
                facets = FakeNotificationAdminService.SAMPLE_FACETS,
                actionChoice = ActionChoice.URL,
                urlText = "https://vifi.app",
            ),
            actions = NotificationComposerActions(),
            onBack = {},
            onOpenHistory = {},
        )
    }
}

@ViFiThemePreviews
@Composable
private fun NotificationComposerConfirmationPreview() {
    ViFiTheme {
        val state = NotificationComposerUiState(title = "Başlık", body = "Mesaj")
        NotificationComposerContent(
            state = state.copy(
                isConfirming = true,
                confirmation = NotificationSendConfirmation(state.draft, NotificationPreview(40, 52)),
            ),
            actions = NotificationComposerActions(),
            onBack = {},
            onOpenHistory = {},
        )
    }
}
