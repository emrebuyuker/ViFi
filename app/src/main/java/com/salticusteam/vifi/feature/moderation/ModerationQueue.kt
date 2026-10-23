package com.salticusteam.vifi.feature.moderation

import com.salticusteam.vifi.core.data.repository.ModerationService
import com.salticusteam.vifi.core.model.ExamSubmissionRecord
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The loaded moderation queue, shared by the queue, detail and file-preview destinations (iOS shares one
 * `ModerationViewModel` between `ModerationView` and its pushed detail). Routes carry only a submission id; the
 * record itself is read from here. An approved or rejected submission is removed at once, so the queue never
 * shows it again (optimistic, like iOS).
 */
@Singleton
class ModerationQueue @Inject constructor(
    private val moderationService: ModerationService,
) {

    // State
    /** The pending submissions, newest first; `null` until the first successful load. */
    private val _records = MutableStateFlow<List<ExamSubmissionRecord>?>(null)
    val records: StateFlow<List<ExamSubmissionRecord>?> = _records.asStateFlow()

    // Intents
    /** Reloads the queue from the backend; throws `ModerationError` and keeps the previous list on failure. */
    suspend fun reload() {
        _records.value = moderationService.pendingSubmissions()
    }

    fun record(id: String): ExamSubmissionRecord? = _records.value?.firstOrNull { it.id == id }

    fun remove(id: String) {
        _records.update { records -> records?.filterNot { it.id == id } }
    }
}
