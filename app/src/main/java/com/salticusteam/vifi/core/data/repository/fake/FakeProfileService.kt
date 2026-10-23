package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.ProfileService
import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.ProfileError
import com.salticusteam.vifi.core.model.UserProfile
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/**
 * In-memory profiles for the mock flavor, previews and tests (iOS MockProfileService / StubProfileService).
 * [failure] makes every call throw; [gate], when set, holds each call until completed (ordering tests).
 */
class FakeProfileService(
    profiles: Map<String, UserProfile> = emptyMap(),
    private val latency: Duration = Duration.ZERO,
    var failure: ProfileError? = null,
) : ProfileService {

    sealed interface Call {
        data class Load(val uid: String) : Call
        data class Save(val profile: UserProfile, val uid: String) : Call
        data class Delete(val uid: String) : Call
    }

    private val stored = profiles.toMutableMap()
    private val _calls = mutableListOf<Call>()
    val calls: List<Call> get() = _calls

    var gate: CompletableDeferred<Unit>? = null

    fun storedProfile(uid: String): UserProfile? = stored[uid]

    override suspend fun loadProfile(uid: String): UserProfile? {
        _calls += Call.Load(uid)
        await()
        return stored[uid]
    }

    override suspend fun saveProfile(profile: UserProfile, uid: String) {
        _calls += Call.Save(profile, uid)
        await()
        stored[uid] = profile
    }

    override suspend fun deleteProfile(uid: String) {
        _calls += Call.Delete(uid)
        await()
        stored.remove(uid)
    }

    private suspend fun await() {
        if (latency > Duration.ZERO) delay(latency)
        gate?.await()
        failure?.let { throw it }
    }

    companion object {
        const val SAMPLE_UID = "mock-user"

        val SAMPLE_PROFILE = UserProfile(
            name = "Ayşe",
            surname = "Yılmaz",
            university = "Boğaziçi Üniversitesi",
            universityId = "bogazici-universitesi",
            universityIsCustom = false,
            faculty = "Mühendislik Fakültesi",
            facultyId = "muhendislik-fakultesi",
            facultyIsCustom = false,
            department = "Bilgisayar Mühendisliği",
            departmentId = "bilgisayar-muhendisligi",
            departmentIsCustom = false,
            gender = Gender.FEMALE,
            phoneNumber = "+905321234567",
        )
    }
}
