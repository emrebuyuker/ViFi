package com.salticusteam.vifi.feature.moderation

import com.salticusteam.vifi.core.data.repository.SubmitterProfileReader
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.SubmitterInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What is known about a submission owner while the moderator reads the queue (iOS `SubmitterState`). */
sealed interface SubmitterState {
    data object Loading : SubmitterState

    data class Loaded(val info: SubmitterInfo) : SubmitterState

    /** The profile does not exist (for example the account was deleted). */
    data object Missing : SubmitterState

    data object Failed : SubmitterState
}

/**
 * Submitter lookups by owner uid, shared by the queue rows and the detail (iOS `ModerationViewModel.submitters`): a uid
 * is read once; a failed read is retried the next time it is asked. Never blocks approving or rejecting.
 *
 * Follows [SessionStore] like the other session-bound stores: personal data of submitters is dropped whenever the
 * signed-in user changes or signs out (account deletion included), and a read still in flight from before is discarded.
 */
@Singleton
class SubmitterDirectory @Inject constructor(
    private val reader: SubmitterProfileReader,
    sessionStore: SessionStore,
    @ApplicationScope scope: CoroutineScope,
) {

    // State
    private val _submitters = MutableStateFlow<Map<String, SubmitterState>>(emptyMap())
    val submitters: StateFlow<Map<String, SubmitterState>> = _submitters.asStateFlow()

    // Internal state
    /** Bumped on every session change; a read started under an older generation is not stored. */
    private val generation = MutableStateFlow(0)

    init {
        scope.launch {
            sessionStore.state
                .map { (it as? SessionState.SignedIn)?.user?.id }
                .distinctUntilChanged()
                .drop(1) // the user the directory starts with; every later change (another user, signed out) clears
                .collect { clear() }
        }
    }

    // Intents
    /** Looks up [uid] unless it is loading, loaded or known missing (iOS `loadSubmitter(uid:)`). */
    suspend fun load(uid: String) {
        val started = generation.value
        // Claims the uid atomically, so two rows asking at once read it once.
        val previous = _submitters.getAndUpdate { map ->
            when (map[uid]) {
                SubmitterState.Loading, is SubmitterState.Loaded, SubmitterState.Missing -> map
                SubmitterState.Failed, null -> map + (uid to SubmitterState.Loading)
            }
        }[uid]
        when (previous) {
            SubmitterState.Loading, is SubmitterState.Loaded, SubmitterState.Missing -> return
            SubmitterState.Failed, null -> Unit
        }
        val result: SubmitterState = try {
            reader.submitter(uid)?.let { SubmitterState.Loaded(it) } ?: SubmitterState.Missing
        } catch (e: CancellationException) {
            // Asked again on the next appearance.
            if (generation.value == started) _submitters.update { it - uid }
            throw e
        } catch (e: Exception) {
            SubmitterState.Failed
        }
        if (generation.value == started) _submitters.update { it + (uid to result) }
    }

    // Helpers
    private fun clear() {
        generation.update { it + 1 }
        _submitters.value = emptyMap()
    }
}
