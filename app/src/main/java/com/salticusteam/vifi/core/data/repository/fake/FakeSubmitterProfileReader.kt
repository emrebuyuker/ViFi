package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.SubmitterProfileReader
import com.salticusteam.vifi.core.model.ProfileError
import com.salticusteam.vifi.core.model.SubmitterInfo

/**
 * In-memory [SubmitterProfileReader] (iOS mock): [profiles] by uid, by default the mock moderation queue's owner.
 * Set [error] to make every call throw it; [readUids] records every read (for caching tests).
 */
class FakeSubmitterProfileReader(
    private val profiles: Map<String, SubmitterInfo> = mapOf(SAMPLE_UID to SAMPLE),
    var error: ProfileError? = null,
) : SubmitterProfileReader {

    val readUids = mutableListOf<String>()

    override suspend fun submitter(uid: String): SubmitterInfo? {
        readUids += uid
        error?.let { throw it }
        return profiles[uid]
    }

    companion object {
        /** The owner of `FakeModerationService.SAMPLES`. */
        const val SAMPLE_UID = "mock-user"

        val SAMPLE = SubmitterInfo(
            fullName = "Ayşe Demir",
            university = "Boğaziçi Üniversitesi",
            faculty = "Mühendislik Fakültesi",
            department = "Bilgisayar Mühendisliği",
        )
    }
}
