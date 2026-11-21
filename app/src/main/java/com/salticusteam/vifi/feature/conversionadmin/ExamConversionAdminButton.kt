package com.salticusteam.vifi.feature.conversionadmin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.salticusteam.vifi.core.model.ExamConversion
import com.salticusteam.vifi.core.model.userMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Yeni yapıya geçir" for one archive exam: the admin check, the queued job and the last failure (iOS state). */
data class ExamConversionButtonUiState(
    /** The signed-in user carries the admin claim; the button is hidden until this is known and `true`. */
    val isAdmin: Boolean = false,
    val isWorking: Boolean = false,
    /** The job queued by the last request (an active job comes back unchanged). */
    val job: ExamConversion? = null,
    val error: String? = null,
)

/**
 * Drives [ExamConversionAdminButton] (ADX-04, K12a; iOS `ExamConversionAdminButton`): checks the admin claim, queues
 * `requestExamConversion {examId, provider}` with the provider the admin picked, and keeps the job's status.
 */
@HiltViewModel(assistedFactory = ExamConversionButtonViewModel.Factory::class)
class ExamConversionButtonViewModel @AssistedInject constructor(
    @Assisted private val examId: String,
    private val service: ExamConversionAdminService,
    private val auth: AuthService,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(ExamConversionButtonUiState())
    val uiState: StateFlow<ExamConversionButtonUiState> = _uiState.asStateFlow()

    @AssistedFactory
    interface Factory {
        fun create(examId: String): ExamConversionButtonViewModel
    }

    // Intents
    init {
        viewModelScope.launch {
            val isAdmin = auth.isCurrentUserAdmin()
            _uiState.update { it.copy(isAdmin = isAdmin) }
        }
    }

    /** Queues the conversion with [provider]; ignored while a request runs. */
    fun start(provider: AIProvider) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                val job = service.requestConversion(examId, provider)
                _uiState.update { it.copy(job = job) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.userMessage) }
            } finally {
                _uiState.update { it.copy(isWorking = false) }
            }
        }
    }
}

/**
 * "Yeni yapıya geçir" for one archive exam (ADX-04, K12a), a self-contained admin component: it hides itself unless the
 * signed-in user is an admin, asks for the AI provider, queues the conversion and shows the job's status.
 *
 * Hooking (SIT-01): place `ExamConversionAdminButton(examId)` in the exam viewer's bottom bar or a section of the
 * legacy image/PDF screen, only for exams that are not yet structured. [examId] is the Firestore `exams` document id
 * (the last component of the exam's `ArchivePath`).
 */
@Composable
fun ExamConversionAdminButton(
    examId: String,
    modifier: Modifier = Modifier,
    viewModel: ExamConversionButtonViewModel = hiltViewModel<ExamConversionButtonViewModel, ExamConversionButtonViewModel.Factory>(
        key = examId,
    ) { it.create(examId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExamConversionAdminButtonContent(state = state, onStart = viewModel::start, modifier = modifier)
}

/** Stateless button content; previews and UI tests drive this. Nothing is drawn for a non-admin. */
@Composable
fun ExamConversionAdminButtonContent(
    state: ExamConversionButtonUiState,
    onStart: (AIProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isAdmin) return
    var isPickingProvider by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = { isPickingProvider = true },
                enabled = !state.isWorking,
                modifier = Modifier.testTag("examAdmin.convert"),
            ) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                Text(stringResource(R.string.conversion_admin_convert_button), modifier = Modifier.padding(start = 8.dp))
                if (state.isWorking) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp).size(16.dp), strokeWidth = 2.dp)
                }
            }
            state.job?.let { job ->
                Text(
                    text = job.status.statusText(job.provider),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("examAdmin.convert.status"),
                )
            }
            state.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("examAdmin.convert.error"),
                )
            }
        }
    }

    if (isPickingProvider) {
        AiProviderDialog(
            title = stringResource(R.string.conversion_admin_convert_provider_title),
            onPick = {
                isPickingProvider = false
                onStart(it)
            },
            onDismiss = { isPickingProvider = false },
        )
    }
}
