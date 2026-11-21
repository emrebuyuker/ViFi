package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.SubmitterInfo

/**
 * Reads the display-only profile of a submission's owner for the moderation screens (4.2.0, iOS
 * `SubmitterProfileReading`): RTDB `users/<uid>/profile` through `SubmitterInfoParser` (admin-only; the rules let an
 * admin read any profile). Throws `ProfileError` on failure; the UI treats a failure like a missing profile.
 */
interface SubmitterProfileReader {
    /** The submitter's info, or `null` when the profile is missing (for example a deleted account). */
    suspend fun submitter(uid: String): SubmitterInfo?
}
