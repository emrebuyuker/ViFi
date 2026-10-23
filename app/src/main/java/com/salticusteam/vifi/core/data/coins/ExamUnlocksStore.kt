package com.salticusteam.vifi.core.data.coins

import com.salticusteam.vifi.core.data.repository.UnlocksReader
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.util.LogCategory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The signed-in user's unlocked exams, shared app-wide so the archive shows lock state consistently (iOS
 * ExamUnlocksStore).
 *
 * Loads once per signed-in uid from [SessionStore] and clears on sign-out or a user change; a newer user
 * cancels an older load. An exam just unlocked is added optimistically ([markUnlocked]) with no re-read. A failed
 * load leaves the set empty — locking more is the safe default.
 */
@Singleton
class ExamUnlocksStore @Inject constructor(
    private val reader: UnlocksReader,
    sessionStore: SessionStore,
    @ApplicationScope scope: CoroutineScope,
) {

    // State
    private val _unlockedIds = MutableStateFlow<Set<String>>(emptySet())
    val unlockedIds: StateFlow<Set<String>> = _unlockedIds.asStateFlow()

    // Intents
    init {
        scope.launch {
            sessionStore.state
                .map { (it as? SessionState.SignedIn)?.user?.id }
                .distinctUntilChanged()
                .collectLatest { uid ->
                    _unlockedIds.value = emptySet()
                    if (uid != null) load(uid)
                }
        }
    }

    fun isUnlocked(examId: String): Boolean = examId in _unlockedIds.value

    /** Reflects an exam the user just unlocked, without a re-read. */
    fun markUnlocked(examId: String) {
        _unlockedIds.update { it + examId }
    }

    // Helpers
    private suspend fun load(uid: String) {
        try {
            val loaded = reader.unlockedExamIds(uid)
            // Merged, so an exam unlocked while the read was in flight stays unlocked.
            _unlockedIds.update { it + loaded }
        } catch (e: CancellationException) {
            throw e // a newer user or sign-out superseded this load
        } catch (e: Exception) {
            LogCategory.APP.log.e(e, "Loading unlocks failed")
        }
    }
}
