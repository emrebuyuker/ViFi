package com.salticusteam.vifi.core.data.preferences.fake

import com.salticusteam.vifi.core.data.preferences.AnnouncementReadPreferences
import com.salticusteam.vifi.core.data.preferences.OnboardingPreferences
import com.salticusteam.vifi.core.data.preferences.RecentExamsRepository
import com.salticusteam.vifi.core.data.preferences.UserPreferencesRepository
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.OnboardingState
import com.salticusteam.vifi.core.model.RecentExam
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory user preferences for the mock flavor, previews and tests. */
class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val skipped = MutableStateFlow<String?>(null)
    private val prompted = MutableStateFlow(false)
    private val prefetch = MutableStateFlow(false)

    override val skippedUpdateVersion: Flow<String?> = skipped
    override val notificationsPrompted: Flow<Boolean> = prompted
    override val prefetchDone: Flow<Boolean> = prefetch

    override suspend fun setSkippedUpdateVersion(version: String?) { skipped.value = version }
    override suspend fun setNotificationsPrompted(prompted: Boolean) { this.prompted.value = prompted }
    override suspend fun setPrefetchDone(done: Boolean) { prefetch.value = done }
}

/** In-memory onboarding flag; starts as a fresh install ([OnboardingState.Presenting]) unless [seen]. */
class FakeOnboardingPreferences(seen: Boolean = false) : OnboardingPreferences {
    private val seenFlag = MutableStateFlow(seen)

    override val state: Flow<OnboardingState> =
        seenFlag.map { if (it) OnboardingState.Hidden else OnboardingState.Presenting }

    override suspend fun markSeen() { seenFlag.value = true }
    override suspend fun seed(seen: Boolean) { seenFlag.value = seen }
}

/** In-memory recents sharing the real list rules ([RecentExam.recorded]). */
class FakeRecentExamsRepository(initial: List<RecentExam> = emptyList()) : RecentExamsRepository {
    private val list = MutableStateFlow(initial)

    override val exams: Flow<List<RecentExam>> = list

    override suspend fun record(path: ArchivePath, kind: ExamKind, at: Long) {
        list.update { RecentExam.recorded(it, path, kind, at) }
    }

    override suspend fun remove(path: ArchivePath) {
        list.update { current -> current.filter { it.path != path } }
    }

    override suspend fun clear() { list.value = emptyList() }
}

/** In-memory read state of the announcements. */
class FakeAnnouncementReadPreferences(initial: Set<String> = emptySet()) : AnnouncementReadPreferences {
    private val ids = MutableStateFlow(initial)

    override val readIds: Flow<Set<String>> = ids

    override suspend fun markRead(ids: Collection<String>) { this.ids.update { it + ids } }
}
