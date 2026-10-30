package com.salticusteam.vifi.feature.notificationcomposer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.notifications.admin.EncodedNotificationImage
import com.salticusteam.vifi.core.data.notifications.admin.NotificationImageEncoder
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.NotificationAdminService
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationLocation
import com.salticusteam.vifi.core.model.NotificationSendResult
import com.salticusteam.vifi.core.model.NotificationStatus
import com.salticusteam.vifi.core.model.userMessage
import com.salticusteam.vifi.core.util.trackScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the admin "Bildirim Gönder" form (iOS `NotificationComposerViewModel`): the draft's fields, the cascading
 * audience pickers fed by the audience facets, live validation and the reach estimate, and the send flow (preview,
 * confirm, upload the image, send).
 *
 * The server re-validates everything; this only keeps the admin from sending something it would refuse. The draft id
 * stays the same across retries of one send (the server answers a repeat with the first result) and is replaced once
 * a send has gone through. The image is uploaded under the id of the notification it belongs to (a test send under
 * its own fresh id), right before that notification is sent; changing the image after an upload starts a new id,
 * because an uploaded image can never be replaced.
 */
@HiltViewModel
class NotificationComposerViewModel @Inject constructor(
    private val service: NotificationAdminService,
    private val imageEncoder: NotificationImageEncoder,
    private val analytics: AnalyticsTracker,
    private val clock: Clock,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(NotificationComposerUiState(now = clock.instant()))
    val uiState: StateFlow<NotificationComposerUiState> = _uiState.asStateFlow()

    // Internal state
    /** Notification ids an image upload was attempted under. */
    private val imageUploadIds = mutableSetOf<String>()

    /** Bumped by every pick and removal, so a slow encode of an earlier pick never replaces a later one. */
    private var imageGeneration = 0

    // Intents
    init {
        loadFacets()
    }

    fun onScreenShown() {
        analytics.trackScreen(AnalyticsScreen.NOTIFICATION_COMPOSER)
    }

    /** Retries the audience pickers' data after a failure. */
    fun reloadFacets() = loadFacets()

    /** The "Tür" control; a change while the kind is locked on Kampanya is ignored. */
    fun onKindSelected(kind: NotificationKind) {
        edit { state ->
            if (state.isKindLocked) state else state.copy(kind = kind)
        }
    }

    fun onTitleChange(title: String) {
        edit { it.copy(title = title) }
    }

    fun onBodyChange(body: String) {
        edit { it.copy(body = body) }
    }

    /** The other mode's selections are kept, so switching back and forth loses nothing. */
    fun onAudienceModeChange(mode: AudienceMode) {
        edit { it.copy(audienceMode = mode) }
    }

    /** Changing a level resets the levels below it, so the selection never mixes values from different branches. */
    fun onUniversitySelected(id: String?) {
        edit { state ->
            if (id == state.universityId) state else state.copy(universityId = id, facultyId = null, departmentId = null)
        }
    }

    fun onFacultySelected(id: String?) {
        edit { state ->
            if (id == state.facultyId) state else state.copy(facultyId = id, departmentId = null)
        }
    }

    /** A department is only meaningful under a faculty. */
    fun onDepartmentSelected(id: String?) {
        edit { state ->
            state.copy(departmentId = if (state.facultyId == null) null else id)
        }
    }

    fun onGenderSelected(gender: Gender?) {
        edit { it.copy(gender = gender) }
    }

    fun onActionChoiceChange(choice: ActionChoice) {
        edit { it.copy(actionChoice = choice) }
    }

    fun onUrlTextChange(text: String) {
        edit { it.copy(urlText = text) }
    }

    fun onExamPicked(exam: ArchiveItem) {
        edit { it.copy(selectedExam = exam) }
    }

    fun onLocationPicked(location: NotificationLocation) {
        edit { it.copy(location = location) }
    }

    /** Turns the delayed send on (an hour from now) or off. */
    fun onSchedulingToggle(isOn: Boolean) {
        edit { state ->
            val now = clock.instant()
            val scheduledAt = if (isOn) now + SCHEDULE_LEAD else null
            val delivery = scheduledAt ?: now
            // A validity set against the old time would otherwise end up before the new delivery.
            val expiresAt = state.expiresAt?.let { if (isOn && !it.isAfter(delivery)) delivery + DEFAULT_VALIDITY else it }
            state.copy(scheduledAt = scheduledAt, expiresAt = expiresAt)
        }
    }

    /** A picked delivery time; the validity is not moved along (a conflict shows as an issue). */
    fun onScheduledAtChange(at: Instant) {
        edit { it.copy(scheduledAt = at) }
    }

    /** Turns the validity period on (a week after delivery) or off. */
    fun onExpiryToggle(isOn: Boolean) {
        edit { state ->
            state.copy(expiresAt = if (isOn) (state.scheduledAt ?: clock.instant()) + DEFAULT_VALIDITY else null)
        }
    }

    fun onExpiresAtChange(at: Instant) {
        edit { it.copy(expiresAt = at) }
    }

    /** Replaces the image with the picked photo at [uri], encoded off the main thread. */
    fun onImagePicked(uri: String) {
        val generation = ++imageGeneration
        edit { it.copy(isPreparingImage = true, imageError = null) }
        viewModelScope.launch {
            val encoded = imageEncoder.encode(uri)
            if (generation != imageGeneration) return@launch
            when (encoded) {
                is EncodedNotificationImage.Jpeg -> {
                    edit { it.copy(image = ComposerImage(encoded.bytes), isPreparingImage = false) }
                    imageDidChange()
                }
                EncodedNotificationImage.Unreadable ->
                    edit { it.copy(isPreparingImage = false, imageError = ComposerImageError.UNREADABLE) }
                EncodedNotificationImage.Unusable ->
                    edit { it.copy(isPreparingImage = false, imageError = ComposerImageError.UNUSABLE) }
            }
        }
    }

    fun onImageRemoved() {
        imageGeneration++
        edit { it.copy(image = null, isPreparingImage = false, imageError = null) }
        imageDidChange()
    }

    /** Asks the server for the exact reach and raises the confirmation dialog; nothing is sent yet. */
    fun requestSend() {
        val state = edit { it }
        if (!state.canSend) return
        val draft = state.draft
        beginWork()
        viewModelScope.launch {
            try {
                val preview = service.preview(draft.effectiveKind, draft.audience)
                edit { it.copy(confirmation = NotificationSendConfirmation(draft, preview), isConfirming = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                edit { it.copy(error = ComposerError.Message(e.userMessage)) }
            } finally {
                edit { it.copy(isWorking = false) }
            }
        }
    }

    /**
     * Sends the draft the admin confirmed. Success resets the form for the next notification; a failure keeps it
     * (and the draft id) so the same send can be retried.
     */
    fun confirmSend() {
        val state = _uiState.value
        val confirmation = state.confirmation
        if (state.isWorking || confirmation == null) return
        beginWork()
        viewModelScope.launch {
            try {
                val result = service.send(withUploadedImage(confirmation.draft))
                edit { it.copy(confirmation = null) }
                handle(result)
            } catch (e: CancellationException) {
                edit { it.copy(confirmation = null) }
                throw e
            } catch (e: Exception) {
                edit { it.copy(confirmation = null, error = ComposerError.Message(e.userMessage)) }
            } finally {
                edit { it.copy(isWorking = false) }
            }
        }
    }

    /** "Vazgeç", a tap outside the dialog or back: nothing is sent and the form stays. */
    fun cancelConfirmation() {
        edit { it.copy(isConfirming = false, confirmation = null) }
    }

    /** Sends the notification to the admin only, under a fresh id so the real one is not used up. */
    fun sendTest() {
        val state = edit { it }
        if (!state.canSendTest) return
        val draft = state.testDraft(NotificationDraft.newId())
        if (state.issue(draft) != null) return
        beginWork()
        viewModelScope.launch {
            try {
                // The result is not inspected (iOS parity): the test reaches only the admin, who sees it arrive.
                service.send(withUploadedImage(draft))
                edit { it.copy(success = ComposerSuccess.TestSent) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                edit { it.copy(error = ComposerError.Message(e.userMessage)) }
            } finally {
                edit { it.copy(isWorking = false) }
            }
        }
    }

    // Helpers
    /** Applies [transform] and re-evaluates the derived values at the current time; returns the new state. */
    private inline fun edit(transform: (NotificationComposerUiState) -> NotificationComposerUiState): NotificationComposerUiState {
        _uiState.update { transform(it).copy(now = clock.instant()) }
        return _uiState.value
    }

    /** Also takes the dialog down: the confirmed send no longer needs it. */
    private fun beginWork() {
        edit { it.copy(isWorking = true, isConfirming = false, error = null, success = null) }
    }

    private fun handle(result: NotificationSendResult) {
        when (result.status) {
            NotificationStatus.SENT, NotificationStatus.SENDING -> {
                resetDraft()
                edit { it.copy(success = ComposerSuccess.Sent(result.recipients)) }
            }
            // No recipient count: the server resolves the audience when the notification goes out, so it reports 0.
            NotificationStatus.SCHEDULED -> {
                resetDraft()
                edit { it.copy(success = ComposerSuccess.Scheduled(result.scheduledAt)) }
            }
            // The server kept that id as a failed record; a retry has to be a new notification.
            NotificationStatus.FAILED, NotificationStatus.CANCELED ->
                edit { it.copy(error = ComposerError.SendFailed, draftId = NotificationDraft.newId()) }
        }
    }

    /** Back to an empty form for the next notification, with a new id. The loaded facets are kept. */
    private fun resetDraft() {
        imageGeneration++
        edit { state ->
            NotificationComposerUiState(
                facets = state.facets,
                facetsError = state.facetsError,
                isWorking = state.isWorking,
                error = state.error,
                success = state.success,
            )
        }
    }

    /** An image already uploaded under the current id cannot be replaced, so a different image needs a new id. */
    private fun imageDidChange() {
        edit { state -> if (state.draftId in imageUploadIds) state.copy(draftId = NotificationDraft.newId()) else state }
    }

    /** [draft] with its image uploaded under its own id (the server accepts no other path). */
    private suspend fun withUploadedImage(draft: NotificationDraft): NotificationDraft {
        val jpeg = _uiState.value.image?.jpeg ?: return draft
        imageUploadIds += draft.id
        return draft.copy(imagePath = service.uploadImage(jpeg, draft.id))
    }

    private fun loadFacets() {
        // A reload after a failure shows the loading state again; loaded facets are not replaced by it.
        edit { if (it.facets == null) it.copy(facetsError = null) else it }
        viewModelScope.launch {
            try {
                val facets = service.audience()
                edit { it.copy(facets = facets, facetsError = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                edit { it.copy(facets = null, facetsError = e.userMessage) }
            }
        }
    }

    private companion object {
        val SCHEDULE_LEAD: Duration = Duration.ofHours(1)

        /** How long a notification stays visible when the admin turns the validity on. */
        val DEFAULT_VALIDITY: Duration = Duration.ofDays(7)
    }
}
