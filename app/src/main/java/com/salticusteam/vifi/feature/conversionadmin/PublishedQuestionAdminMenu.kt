package com.salticusteam.vifi.feature.conversionadmin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.repository.AuthService
import com.salticusteam.vifi.core.data.repository.ExamConversionAdminService
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The admin menu's message dialog (iOS `message`). */
sealed interface PublishedQuestionMessage {
    /** The regeneration was accepted; it finishes a few seconds later on the server. */
    data object Regenerating : PublishedQuestionMessage

    data class Failure(val text: String) : PublishedQuestionMessage
}

/** The published-question admin menu: the admin check, a running action and its message. */
data class PublishedQuestionAdminUiState(
    val isAdmin: Boolean = false,
    val isWorking: Boolean = false,
    val message: PublishedQuestionMessage? = null,
)

/** One-shot results the screen reacts to; every branch must be handled (no `else`). */
sealed interface PublishedQuestionAdminEvent {
    /** An edit or a regeneration request succeeded: the host re-reads the questions (iOS `onChange`). */
    data object Changed : PublishedQuestionAdminEvent
}

/**
 * Drives [PublishedQuestionAdminMenu] (ADX-05, K15; iOS `PublishedQuestionAdminActions`): edits go through
 * `updatePublishedQuestion`, regeneration through `regeneratePublishedQuestion` with the picked provider. Changes are
 * live at once; the question id never changes.
 */
@HiltViewModel
class PublishedQuestionAdminViewModel @Inject constructor(
    private val service: ExamConversionAdminService,
    private val auth: AuthService,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(PublishedQuestionAdminUiState())
    val uiState: StateFlow<PublishedQuestionAdminUiState> = _uiState.asStateFlow()

    // One-shot events
    private val _events = Channel<PublishedQuestionAdminEvent>(Channel.BUFFERED)
    val events: Flow<PublishedQuestionAdminEvent> = _events.receiveAsFlow()

    // Intents
    init {
        viewModelScope.launch {
            val isAdmin = auth.isCurrentUserAdmin()
            _uiState.update { it.copy(isAdmin = isAdmin) }
        }
    }

    /** Saves the editor's result; returns the error message to show in the editor, or `null` on success. */
    suspend fun saveEdit(edited: ExamQuestion): String? =
        try {
            service.updatePublishedQuestion(edited)
            _events.send(PublishedQuestionAdminEvent.Changed)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.userMessage
        }

    fun regenerate(question: ExamQuestion, provider: AIProvider) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            try {
                service.regeneratePublishedQuestion(question.id, provider)
                _uiState.update { it.copy(message = PublishedQuestionMessage.Regenerating) }
                _events.send(PublishedQuestionAdminEvent.Changed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(message = PublishedQuestionMessage.Failure(e.userMessage)) }
            } finally {
                _uiState.update { it.copy(isWorking = false) }
            }
        }
    }

    fun onMessageDismissed() = _uiState.update { it.copy(message = null) }
}

/**
 * Per-question admin actions on a published structured exam (ADX-05, K15; iOS `PublishedQuestionAdminActions`):
 * "Düzenle" (opens the editor, saves through `updatePublishedQuestion`) and "Yeniden üret" (provider choice, then
 * `regeneratePublishedQuestion`), under one "Yönetici" menu. A self-contained component that draws nothing for a
 * non-admin. Changes go live at once; the question id is permanent.
 *
 * Hooking (SIT-01): place `PublishedQuestionAdminMenu(question, onChange)` in the slot above the question card
 * (outside the card's content); [onChange] is called after a successful edit or regeneration request, so the screen can
 * re-read the questions (a regeneration finishes a few seconds later on the server).
 */
@Composable
fun PublishedQuestionAdminMenu(
    question: ExamQuestion,
    modifier: Modifier = Modifier,
    onChange: () -> Unit = {},
    // One per question: the events of one menu never reach another card's menu.
    viewModel: PublishedQuestionAdminViewModel = hiltViewModel(key = question.id),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnChange by rememberUpdatedState(onChange)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PublishedQuestionAdminEvent.Changed -> currentOnChange()
            }
        }
    }

    PublishedQuestionAdminMenuContent(
        question = question,
        state = state,
        onSaveEdit = viewModel::saveEdit,
        onRegenerate = { provider -> viewModel.regenerate(question, provider) },
        onMessageDismissed = viewModel::onMessageDismissed,
        modifier = modifier,
    )
}

/** Stateless menu content; previews and UI tests drive this. Nothing is drawn for a non-admin. */
@Composable
fun PublishedQuestionAdminMenuContent(
    question: ExamQuestion,
    state: PublishedQuestionAdminUiState,
    onSaveEdit: suspend (ExamQuestion) -> String?,
    onRegenerate: (AIProvider) -> Unit,
    onMessageDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isAdmin) return
    var isMenuOpen by remember { mutableStateOf(false) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var isPickingProvider by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.padding(bottom = 6.dp)) {
        OutlinedButton(
            onClick = { isMenuOpen = true },
            enabled = !state.isWorking,
            modifier = Modifier.testTag("questionAdmin.menu"),
        ) {
            Icon(Icons.Filled.Build, contentDescription = null)
            Text(stringResource(R.string.conversion_admin_question_menu), modifier = Modifier.padding(start = 8.dp))
        }
        DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.conversion_admin_draft_edit)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    isMenuOpen = false
                    isEditing = true
                },
                modifier = Modifier.testTag("questionAdmin.edit"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.conversion_admin_draft_regenerate)) },
                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                onClick = {
                    isMenuOpen = false
                    isPickingProvider = true
                },
                modifier = Modifier.testTag("questionAdmin.regenerate"),
            )
        }
    }

    if (isEditing) {
        QuestionEditorDialog(question = question, onSave = onSaveEdit, onDismiss = { isEditing = false })
    }
    if (isPickingProvider) {
        AiProviderDialog(
            title = stringResource(R.string.conversion_admin_review_regenerate_question_provider),
            onPick = {
                isPickingProvider = false
                onRegenerate(it)
            },
            onDismiss = { isPickingProvider = false },
        )
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = onMessageDismissed,
            title = { Text(stringResource(R.string.conversion_admin_question_menu)) },
            text = {
                Text(
                    text = when (message) {
                        PublishedQuestionMessage.Regenerating -> stringResource(R.string.conversion_admin_question_regenerating)
                        is PublishedQuestionMessage.Failure -> message.text
                    },
                    modifier = Modifier.testTag("questionAdmin.message"),
                )
            },
            confirmButton = {
                TextButton(onClick = onMessageDismissed, modifier = Modifier.testTag("questionAdmin.message.ok")) {
                    Text(stringResource(R.string.conversion_admin_ok))
                }
            },
        )
    }
}
