package com.salticusteam.vifi.core.data.profile

import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.UserProfile

/**
 * Turns a raw `users/<uid>/profile` value (`DataSnapshot.value`) into a [UserProfile], and a profile back
 * into the child values written to the database (iOS UserProfileParser).
 *
 * Pure and Firebase-free so it can be unit-tested with plain maps. The `updatedAt` timestamp is added by the
 * service (a server value), so it is deliberately not part of the written fields here.
 */
object UserProfileParser {

    /** Every key written by [fields]; the rules additionally require `updatedAt` and reject any other key. */
    val FIELD_KEYS = listOf(
        "name", "surname",
        "university", "universityId", "universityIsCustom",
        "faculty", "facultyId", "facultyIsCustom",
        "department", "departmentId", "departmentIsCustom",
        "gender", "phoneNumber",
    )

    const val UPDATED_AT_KEY = "updatedAt"

    /** A profile from a raw snapshot value, or `null` when a required field is missing or malformed. */
    fun profile(value: Any?): UserProfile? {
        val entries = value as? Map<*, *> ?: return null
        return UserProfile(
            name = string(entries["name"]) ?: return null,
            surname = string(entries["surname"]) ?: return null,
            university = string(entries["university"]) ?: return null,
            universityId = string(entries["universityId"]) ?: return null,
            universityIsCustom = bool(entries["universityIsCustom"]),
            faculty = string(entries["faculty"]) ?: return null,
            facultyId = string(entries["facultyId"]) ?: return null,
            facultyIsCustom = bool(entries["facultyIsCustom"]),
            department = string(entries["department"]) ?: return null,
            departmentId = string(entries["departmentId"]) ?: return null,
            departmentIsCustom = bool(entries["departmentIsCustom"]),
            gender = Gender.fromRaw(entries["gender"] as? String) ?: return null,
            phoneNumber = string(entries["phoneNumber"]) ?: return null,
        )
    }

    /** The stored fields of [profile] (without `updatedAt`), for a database write. */
    fun fields(profile: UserProfile): Map<String, Any> = mapOf(
        "name" to profile.name,
        "surname" to profile.surname,
        "university" to profile.university,
        "universityId" to profile.universityId,
        "universityIsCustom" to profile.universityIsCustom,
        "faculty" to profile.faculty,
        "facultyId" to profile.facultyId,
        "facultyIsCustom" to profile.facultyIsCustom,
        "department" to profile.department,
        "departmentId" to profile.departmentId,
        "departmentIsCustom" to profile.departmentIsCustom,
        "gender" to profile.gender.raw,
        "phoneNumber" to profile.phoneNumber,
    )

    /** A non-empty string leaf, or `null`. */
    private fun string(value: Any?): String? = (value as? String)?.takeIf { it.isNotEmpty() }

    /** A boolean leaf; `false` when missing or malformed (tolerant on read). */
    private fun bool(value: Any?): Boolean = value as? Boolean ?: false
}
