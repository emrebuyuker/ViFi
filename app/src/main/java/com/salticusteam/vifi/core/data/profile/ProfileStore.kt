package com.salticusteam.vifi.core.data.profile

import com.salticusteam.vifi.core.data.repository.ProfileService
import com.salticusteam.vifi.core.model.UserProfile
import com.salticusteam.vifi.core.model.userMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the signed-in user has completed their profile; the app gate shows completion or the archive. */
sealed interface ProfileState {
    /** The profile has not been read yet (briefly, after signing in). */
    data object Loading : ProfileState

    /** No profile stored yet: the completion screen is shown. */
    data object Absent : ProfileState

    data class Present(val profile: UserProfile) : ProfileState

    /** The read failed; the user is offered a retry rather than being forced to re-enter a profile. */
    data class Failed(val message: String) : ProfileState
}

/**
 * The process-wide profile gate state (iOS ProfileStore), shared by the gate and the account screen.
 *
 * The gate calls [load] for the signed-in uid (from a `LaunchedEffect(uid)`, so a newer uid cancels the older
 * read) and [reset] on sign-out. The completion and edit screens call [update] after a successful save, so
 * the gate opens without a second read.
 */
@Singleton
class ProfileStore @Inject constructor(
    private val service: ProfileService,
) {

    // State
    private val _state = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    // Internal state
    /** The uid the current state was loaded for, so re-reading the same user does not flash the spinner. */
    private var loadedUid: String? = null

    /** Bumped by every load/update/reset, so a read superseded while in flight never overwrites newer state. */
    private var generation = 0

    /** The loaded profile, if any. */
    val profile: UserProfile? get() = (_state.value as? ProfileState.Present)?.profile

    /**
     * Reads the profile for [uid]. Keeps a profile already loaded for the same user visible while it re-reads,
     * so the gate does not flicker; cancellation leaves the current state untouched.
     */
    suspend fun load(uid: String) {
        val token = ++generation
        if (!(loadedUid == uid && profile != null)) {
            _state.value = ProfileState.Loading
        }
        val next = try {
            service.loadProfile(uid)?.let(ProfileState::Present) ?: ProfileState.Absent
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProfileState.Failed(e.userMessage)
        }
        if (token != generation) return
        loadedUid = uid
        _state.value = next
    }

    /** Reflects a profile the user just saved, opening the gate without a reload. */
    fun update(profile: UserProfile) {
        generation++
        _state.value = ProfileState.Present(profile)
    }

    /** Clears the state on sign-out, so the next user starts from a clean read. */
    fun reset() {
        generation++
        loadedUid = null
        _state.value = ProfileState.Loading
    }
}
