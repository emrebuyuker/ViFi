package com.salticusteam.vifi.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.PracticeService
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.PracticeBatch
import com.salticusteam.vifi.core.model.PracticeLimits
import com.salticusteam.vifi.core.model.PracticeQuestion
import com.salticusteam.vifi.core.model.PracticeRequest
import com.salticusteam.vifi.core.model.PracticeSelection
import com.salticusteam.vifi.core.model.userMessage
import com.salticusteam.vifi.core.util.LogCategory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * One practice session (iOS `PracticeSessionViewModel`): questions one at a time from the server. No right/wrong
 * tracking and no end summary (owner decisions P2, P3).
 *
 * Premium fetches the next question ahead so the callable's latency stays hidden. Free users do not: the server
 * counts the daily limit when it *delivers* a question, so a prefetched question the user never reaches (they leave
 * the session) would spend one of their 5 daily questions for nothing. `exclude` carries the session's seen ids
 * (newest 300), so nothing repeats until the pool is exhausted and the user starts over. Every call asks for one
 * question.
 *
 * All state lives on the main thread; the service call is the only suspension that matters.
 */
@HiltViewModel(assistedFactory = PracticeSessionViewModel.Factory::class)
class PracticeSessionViewModel @AssistedInject constructor(
    @Assisted private val selection: PracticeSelection,
    private val service: PracticeService,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(selection: PracticeSelection): PracticeSessionViewModel
    }

    // State
    private val _uiState = MutableStateFlow(PracticeSessionUiState(selection))
    val uiState: StateFlow<PracticeSessionUiState> = _uiState.asStateFlow()

    // One-shot events
    private val _events = Channel<PracticeSessionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Internal state
    private var phase: PracticePhase = PracticePhase.Loading
    private var current: PracticeQuestion? = null
    private var isAnswerRevealed = false
    private var questionNumber = 0
    private var poolSize = selection.poolCount

    /** Delivered but not yet shown (at most the one prefetched question; Premium only). */
    private val queue = mutableListOf<PracticeQuestion>()
    private val seenIds = mutableListOf<String>()

    /** The free questions left today as the server last reported it; `null` for Premium or before the first answer. */
    private var serverRemaining: Int? = null

    /** Free-limit state: no more questions can be delivered today. */
    private var isLimitHit = false

    /** The server has no unseen question left in the pool. */
    private var isPoolDone = false
    private var lastError: String? = null
    private var prefetchJob: Job? = null
    private var hasStarted = false
    private var hasTrackedReveal = false
    private var hasTrackedLimit = false
    private var isAdvancing = false

    /** The ids sent as `exclude`: the newest [PracticeLimits.MAX_EXCLUDE] seen ones. */
    val excludedIds: List<String>
        get() = seenIds.takeLast(PracticeLimits.MAX_EXCLUDE)

    // Intents
    /** Starts the session once (the screen's effect runs again on every re-composition of a new entry). */
    fun onStart() {
        if (hasStarted) return
        hasStarted = true
        analytics.track(AnalyticsEvent.PracticeStarted(selection.examType, selection.scope))
        launchAdvance()
    }

    /** "Sıradaki soru" (also the retry after a failure). */
    fun onNext() {
        launchAdvance()
    }

    /** "Baştan başla": forgets the seen ids and draws from the whole pool again. */
    fun onRestart() {
        if (isAdvancing) return
        prefetchJob?.cancel()
        prefetchJob = null
        queue.clear()
        seenIds.clear()
        questionNumber = 0
        isPoolDone = false
        current = null
        phase = PracticePhase.Loading
        publish()
        launchAdvance()
    }

    /** Reveals or hides the current answer; the first reveal of each question is counted. */
    fun onToggleAnswer() {
        isAnswerRevealed = !isAnswerRevealed
        if (isAnswerRevealed && !hasTrackedReveal) {
            hasTrackedReveal = true
            analytics.track(AnalyticsEvent.PracticeAnswerRevealed)
        }
        publish()
    }

    /** "Premium'a Göz At" on the limit screen: counted, then the screen opens the paywall. */
    fun onPaywallClicked() {
        analytics.track(AnalyticsEvent.PracticePaywallOpened)
        viewModelScope.launch { _events.send(PracticeSessionEvent.OpenPaywall) }
    }

    // Flow
    private fun launchAdvance() {
        viewModelScope.launch { advance() }
    }

    private suspend fun advance() {
        if (isAdvancing) return
        isAdvancing = true
        try {
            if (queue.isEmpty()) prefetchJob?.join()
            if (queue.isEmpty() && !isLimitHit && !isPoolDone) {
                if (current == null) {
                    phase = PracticePhase.Loading
                    publish()
                }
                lastError = null
                fetchOne()
            }
            val error = lastError
            when {
                queue.isNotEmpty() -> show(queue.removeAt(0))
                error != null -> {
                    phase = PracticePhase.Failed(error)
                    publish()
                }
                isLimitHit -> showLimit()
                isPoolDone -> {
                    current = null
                    phase = PracticePhase.Exhausted
                    publish()
                }
            }
        } finally {
            isAdvancing = false
        }
    }

    private fun show(question: PracticeQuestion) {
        current = question
        questionNumber += 1
        isAnswerRevealed = false
        hasTrackedReveal = false
        phase = PracticePhase.Question
        publish()
        startPrefetch()
    }

    private fun showLimit() {
        current = null
        phase = PracticePhase.LimitReached
        publish()
        if (hasTrackedLimit) return
        hasTrackedLimit = true
        analytics.track(AnalyticsEvent.PracticeLimitReached)
    }

    /** Premium only: after the first answer `serverRemaining` is `null` for Premium and a number for free users. */
    private fun startPrefetch() {
        val running = prefetchJob?.isActive == true
        if (serverRemaining != null || running || queue.isNotEmpty() || isLimitHit || isPoolDone) return
        prefetchJob = viewModelScope.launch {
            fetchOne()
            publish()
        }
    }

    /**
     * Asks the server for one more question and files the answer. A failure is remembered for the screen to show when
     * it is needed (a failed prefetch stays silent until the user asks for the next question).
     */
    private suspend fun fetchOne() {
        val request = PracticeRequest(selection, excludedIds, count = 1)
        try {
            val batch = service.nextQuestions(request)
            // A restart cancelled this call while it was answering: its batch belongs to the old session.
            currentCoroutineContext().ensureActive()
            apply(batch)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.APP.log.e(e, "Practice question fetch failed")
            lastError = e.userMessage
        }
    }

    private fun apply(batch: PracticeBatch) {
        serverRemaining = batch.remainingToday
        poolSize = maxOf(poolSize, batch.poolSize)
        val fresh = batch.questions.filter { it.id !in seenIds }
        for (question in fresh) {
            queue += question
            seenIds += question.id
        }
        if (batch.limitReached || batch.remainingToday == 0) {
            // Free: nothing more can be delivered once today's last question is out.
            isLimitHit = true
        } else if (fresh.isEmpty()) {
            // No unseen question came back (also guards a server that ignored `exclude`).
            isPoolDone = true
        }
    }

    private fun publish() {
        _uiState.value = PracticeSessionUiState(
            selection = selection,
            phase = phase,
            current = current,
            isAnswerRevealed = isAnswerRevealed,
            questionNumber = questionNumber,
            poolSize = poolSize,
            remainingToday = serverRemaining?.plus(queue.size),
        )
    }
}
