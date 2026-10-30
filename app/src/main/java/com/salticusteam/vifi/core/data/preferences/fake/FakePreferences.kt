package com.salticusteam.vifi.core.data.preferences.fake

import com.salticusteam.vifi.core.data.inbox.LegacyInboxReadState
import com.salticusteam.vifi.core.data.preferences.NotificationPromptHistory
import com.salticusteam.vifi.core.data.preferences.OnboardingPreferences
import com.salticusteam.vifi.core.data.preferences.RecentExamsRepository
import com.salticusteam.vifi.core.data.preferences.UserPreferencesRepository
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.OnboardingState
import com.salticusteam.vifi.core.model.RecentExam
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory user preferences for the mock flavor, previews and tests. */
class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val skipped = MutableStateFlow<String?>(null)
    private val prompted = MutableStateFlow(false)
    private val prefetchedAt = MutableStateFlow<Long?>(null)

    override val skippedUpdateVersion: Flow<String?> = skipped
    override val notificationsPrompted: Flow<Boolean> = prompted
    override val archivePrefetchedAt: Flow<Long?> = prefetchedAt

    override suspend fun setSkippedUpdateVersion(version: String?) { skipped.value = version }
    override suspend fun setNotificationsPrompted(prompted: Boolean) { this.prompted.value = prompted }
    override suspend fun setArchivePrefetchedAt(epochMillis: Long) { prefetchedAt.value = epochMillis }
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

/** The pre-4.1.0 read ids in memory; [hasLegacyIds] turns false once cleared. */
class FakeLegacyInboxReadState(hasLegacyIds: Boolean = false) : LegacyInboxReadState {
    var hasLegacyIds = hasLegacyIds
        private set

    var clearCount = 0
        private set

    override suspend fun clear() {
        clearCount++
        hasLegacyIds = false
    }
}

/**
 * The notification prompt history in memory (mock flavor, tests; iOS mock suite wiped at each launch): the mock
 * flavor seeds it per launch argument, so a scenario never depends on what an earlier run left on the device.
 */
class FakeNotificationPromptHistory(private val now: () -> Instant = Instant::now) : NotificationPromptHistory {
    var primingOffered = false
    var consentOffered = false
    var permissionDeclinedAt: Instant? = null
    var consentDeclinedAt: Instant? = null
    var permissionReaskShown = false
    var consentReaskShown = false

    override suspend fun hasOfferedPriming() = primingOffered
    override suspend fun hasOfferedConsent() = consentOffered
    override suspend fun markPrimingOffered() { primingOffered = true }
    override suspend fun markConsentOffered() { consentOffered = true }
    override suspend fun markPermissionReaskShown() { permissionReaskShown = true }
    override suspend fun markConsentReaskShown() { consentReaskShown = true }
    override suspend fun recordPermissionDeclined() { if (permissionDeclinedAt == null) permissionDeclinedAt = now() }
    override suspend fun recordConsentDeclined() { if (consentDeclinedAt == null) consentDeclinedAt = now() }
    override suspend fun shouldReaskPermission() =
        NotificationPromptHistory.isReaskDue(permissionReaskShown, permissionDeclinedAt, now())
    override suspend fun shouldReaskConsent() =
        NotificationPromptHistory.isReaskDue(consentReaskShown, consentDeclinedAt, now())
}
