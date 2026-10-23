package com.salticusteam.vifi.core.data.announcements

import com.salticusteam.vifi.core.data.preferences.AnnouncementReadPreferences
import com.salticusteam.vifi.core.data.repository.AnnouncementsReader
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.Announcement
import com.salticusteam.vifi.core.model.userMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the inbox shows: the announcements (newest first), whether the first read is still pending, and a failure. */
data class AnnouncementsState(
    val announcements: List<Announcement> = emptyList(),
    val isLoading: Boolean = true,
    /** Turkish message of the last failed read; `null` while healthy. */
    val error: String? = null,
)

/**
 * The app-wide announcements (iOS AnnouncementsStore): follows the live list while a user is signed in, and tracks
 * on this device which ones were read. Unread = published and not yet marked read; opening the inbox marks all read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AnnouncementsStore @Inject constructor(
    reader: AnnouncementsReader,
    private val readPreferences: AnnouncementReadPreferences,
    sessionStore: SessionStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val retry = MutableStateFlow(0)

    val state: StateFlow<AnnouncementsState> = combine(sessionStore.state, retry) { session, _ -> session }
        .map { it is SessionState.SignedIn }
        .distinctUntilChanged()
        .flatMapLatest { signedIn -> if (signedIn) loading(reader) else flowOf(AnnouncementsState(isLoading = false)) }
        .stateIn(scope, SharingStarted.Eagerly, AnnouncementsState())

    /** How many announcements this device has not opened yet; drives the bell badge. */
    val unreadCount: StateFlow<Int> = combine(state, readPreferences.readIds) { current, read ->
        current.announcements.count { it.id !in read }
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    /** Marks everything currently listed as read. */
    fun markAllRead() {
        val ids = state.value.announcements.map { it.id }
        if (ids.isEmpty()) return
        scope.launch { readPreferences.markRead(ids) }
    }

    /** Restarts the live read after a failure. */
    fun retry() {
        retry.value++
    }

    private fun loading(reader: AnnouncementsReader): Flow<AnnouncementsState> = reader.announcementsChanges()
        .map { AnnouncementsState(announcements = it, isLoading = false) }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(AnnouncementsState(isLoading = false, error = e.userMessage))
        }
        .onStart { emit(AnnouncementsState()) }
}
