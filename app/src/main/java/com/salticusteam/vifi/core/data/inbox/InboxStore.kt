package com.salticusteam.vifi.core.data.inbox

import com.salticusteam.vifi.core.data.repository.InboxReader
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.InboxError
import com.salticusteam.vifi.core.model.InboxItem
import com.salticusteam.vifi.core.util.LogCategory
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether the inbox was ever opened, as far as this device knows (iOS `InboxStore.ReadMarker`). */
sealed interface ReadMarker {
    /** Not delivered yet: nothing is unread. */
    data object Unknown : ReadMarker

    /** Could not be read: nothing is unread, rather than a badge that never clears. */
    data object Unavailable : ReadMarker

    data class Known(val readAt: Instant) : ReadMarker
}

/** What the inbox shows: the merged, unexpired notifications newest first, and which of them are unread. */
data class InboxState(
    val items: List<InboxItem> = emptyList(),
    val isLoading: Boolean = true,
    /** Both sources failed (Turkish message); the list is empty. */
    val error: String? = null,
    /** Exactly one source failed: the other one's notifications are listed. */
    val hasPartialFailure: Boolean = false,
    val readMarker: ReadMarker = ReadMarker.Unknown,
) {
    /** Newer than the marker (the same instant counts as read); undated items and an unknown marker never are. */
    fun isUnread(item: InboxItem): Boolean {
        val readAt = (readMarker as? ReadMarker.Known)?.readAt ?: return false
        val createdAt = item.createdAt ?: return false
        return createdAt.isAfter(readAt)
    }

    val unreadCount: Int get() = items.count(::isUnread)
}

/**
 * The app-wide inbox (iOS `InboxStore`): the public announcements and the user's own inbox merged (a personal copy
 * wins over the broadcast one), unexpired, newest first; unread is server-side (`settings/inbox.readAt`), so every
 * device counts the same. Observed while a user is signed in (profile completion not needed), reset on sign-out.
 *
 * Confined to the main thread: the session, the screens and the listeners all reach it there.
 */
@Singleton
class InboxStore @Inject constructor(
    private val reader: InboxReader,
    private val legacyReadState: LegacyInboxReadState,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private sealed interface Source {
        data object Pending : Source

        data class Loaded(val items: List<InboxItem>) : Source

        data object Failed : Source
    }

    private data class Sources(
        val uid: String? = null,
        val broadcast: Source = Source.Pending,
        val personal: Source = Source.Pending,
        val marker: ReadMarker = ReadMarker.Unknown,
    )

    // State
    private val sources = MutableStateFlow(Sources())

    // Bumped to drop items that expired since the last change (there is no timer, as on iOS).
    private val expiryCheck = MutableStateFlow(0)

    val state: StateFlow<InboxState> = combine(sources, expiryCheck) { current, _ -> state(current, clock.instant()) }
        .stateIn(scope, SharingStarted.Eagerly, InboxState())

    /** Drives the bell. */
    val unreadCount: StateFlow<Int> = state.map { it.unreadCount }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    // Internal state
    private var job: Job? = null
    private var observation: Job? = null
    private var didInitializeMarker = false
    private var markedThrough: Instant? = null

    // Intents
    /** Follows [sessions] for the process lifetime: a sign-in observes that user, a sign-out resets; later calls do nothing. */
    fun start(sessions: Flow<SessionState>) {
        if (job != null) return
        job = scope.launch {
            sessions.collect { session ->
                when (session) {
                    is SessionState.SignedIn -> if (sources.value.uid != session.user.id) observe(session.user.id)
                    SessionState.SignedOut -> reset()
                    SessionState.Unknown -> Unit
                }
            }
        }
    }

    /** Starts over for the current user: back to loading, every listener reopened (a failed one has ended). */
    fun retry() {
        sources.value.uid?.let(::observe)
    }

    /**
     * Marks everything read in one server write, when something dated is unread and newer than what was marked
     * before. Called when the inbox opens and whenever more arrives while it is open; a failure is retried next time.
     */
    fun markAllRead() {
        val uid = sources.value.uid ?: return
        val current = state.value
        if (current.readMarker !is ReadMarker.Known) return
        val newest = current.items.filter(current::isUnread).mapNotNull { it.createdAt }.maxOrNull() ?: return
        markedThrough?.let { if (!newest.isAfter(it)) return }
        markedThrough = newest
        scope.launch {
            try {
                reader.markRead(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogCategory.APP.log.w(e, "Marking the inbox read failed")
                if (markedThrough == newest) markedThrough = null
            }
        }
    }

    /** Drops what expired since the last change (the inbox calls it when shown). */
    fun reevaluateExpiry() {
        expiryCheck.update { it + 1 }
    }

    // Helpers
    private fun observe(uid: String) {
        observation?.cancel()
        sources.value = Sources(uid = uid)
        didInitializeMarker = false
        markedThrough = null
        observation = scope.launch {
            launch { follow(uid, reader.broadcastChanges()) { copy(broadcast = it) } }
            launch { follow(uid, reader.personalChanges(uid)) { copy(personal = it) } }
            launch { followMarker(uid) }
        }
    }

    private fun reset() {
        observation?.cancel()
        observation = null
        sources.value = Sources()
        didInitializeMarker = false
        markedThrough = null
    }

    private suspend fun follow(uid: String, items: Flow<List<InboxItem>>, apply: Sources.(Source) -> Sources) {
        try {
            items.collect { list -> update(uid) { apply(Source.Loaded(list)) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The real listener ends after a failure; recovery is a retry.
            update(uid) { apply(Source.Failed) }
        }
    }

    private suspend fun followMarker(uid: String) {
        try {
            reader.readMarkerChanges(uid).collect { readAt ->
                if (readAt != null) update(uid) { copy(marker = ReadMarker.Known(readAt)) } else initializeMarker(uid)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never written on a read error.
            update(uid) { copy(marker = ReadMarker.Unavailable) }
        }
    }

    /**
     * The server confirmed there is no marker (a new account, or one from before 4.1.0): what is already out is listed
     * but not unread. Once per observation: the device clock stands in until the server's own time comes back.
     */
    private fun initializeMarker(uid: String) {
        if (didInitializeMarker) return
        didInitializeMarker = true
        update(uid) { copy(marker = ReadMarker.Known(clock.instant())) }
        scope.launch {
            try {
                reader.markRead(uid)
                legacyReadState.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Kept locally for this session; the next observation tries again. The legacy ids stay.
                LogCategory.APP.log.w(e, "Writing the first inbox marker failed")
            }
        }
    }

    /** Ignores what a previous user's listeners deliver late. */
    private fun update(uid: String, change: Sources.() -> Sources) {
        sources.update { if (it.uid == uid) it.change() else it }
    }

    private fun state(current: Sources, now: Instant): InboxState {
        val (_, broadcast, personal, marker) = current
        val bothFailed = broadcast == Source.Failed && personal == Source.Failed
        return when {
            bothFailed -> InboxState(isLoading = false, error = InboxError.Unavailable.userMessage, readMarker = marker)
            broadcast == Source.Pending || personal == Source.Pending -> InboxState(readMarker = marker)
            else -> InboxState(
                items = merged(broadcast.items(), personal.items(), now),
                isLoading = false,
                hasPartialFailure = (broadcast == Source.Failed) != (personal == Source.Failed),
                readMarker = marker,
            )
        }
    }

    private fun Source.items(): List<InboxItem> = (this as? Source.Loaded)?.items.orEmpty()

    internal companion object {
        /** Keyed by id, the personal copy last so it wins; unexpired; newest first, undated last, ties by id. */
        fun merged(broadcast: List<InboxItem>, personal: List<InboxItem>, now: Instant): List<InboxItem> =
            (broadcast + personal).associateBy { it.id }.values
                .filterNot { it.isExpired(now) }
                .sortedWith(NEWEST_FIRST)

        private val NEWEST_FIRST: Comparator<InboxItem> =
            compareByDescending<InboxItem, Instant?>(nullsFirst()) { it.createdAt }.thenBy { it.id }
    }
}
