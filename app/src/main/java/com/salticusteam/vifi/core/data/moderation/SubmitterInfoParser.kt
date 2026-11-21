package com.salticusteam.vifi.core.data.moderation

import com.salticusteam.vifi.core.model.SubmitterInfo
import com.salticusteam.vifi.core.util.RawValues

/**
 * Maps a raw RTDB `users/<uid>/profile` value to a [SubmitterInfo] (iOS `SubmitterInfoParser`), dropping every field
 * the moderator must not see (phone number, gender). A profile without a name and surname is no submitter.
 */
object SubmitterInfoParser {

    fun info(value: Any?): SubmitterInfo? {
        val entries = value as? Map<*, *> ?: return null
        val name = RawValues.nonEmpty(entries["name"]) ?: return null
        val surname = RawValues.nonEmpty(entries["surname"]) ?: return null
        return SubmitterInfo(
            fullName = "$name $surname",
            university = RawValues.nonEmpty(entries["university"]).orEmpty(),
            faculty = RawValues.nonEmpty(entries["faculty"]).orEmpty(),
            department = RawValues.nonEmpty(entries["department"]).orEmpty(),
        )
    }
}
