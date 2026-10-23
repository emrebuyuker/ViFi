package com.salticusteam.vifi.core.data.profile

import com.salticusteam.vifi.core.model.UserProfile

/**
 * Client-side mirror of the `users/$uid/profile` `.validate` rules in the shared backend
 * (`ViFi-iOS/firebase/database.rules.json`). The rules stay the authority; this lets the service refuse a write
 * the server would reject anyway, and lets tests pin the Android payload to the contract.
 *
 * Keep in lockstep with the rules file: when a rule changes there, change it here in the same release.
 */
object ProfileWriteContract {

    private const val MAX_ID_LENGTH = 140
    private val forbiddenCharacters = Regex("[.$#\\[\\]/]")
    private val idPattern = Regex("^[a-z0-9-]+$")
    private val turkishE164 = Regex("^\\+90[0-9]{10}$")

    /**
     * The keys of [profile] the rules would reject, empty when the write is valid. [authPhoneNumber] is the
     * signed-in user's verified number (`auth.token.phone_number`), which `phoneNumber` must equal.
     */
    fun violations(profile: UserProfile, authPhoneNumber: String?): List<String> = buildList {
        if (!isText(profile.name, UserProfile.MAX_NAME_LENGTH)) add("name")
        if (!isText(profile.surname, UserProfile.MAX_NAME_LENGTH)) add("surname")
        if (!isText(profile.university, UserProfile.MAX_FIELD_LENGTH)) add("university")
        if (!isId(profile.universityId)) add("universityId")
        if (!isText(profile.faculty, UserProfile.MAX_FIELD_LENGTH)) add("faculty")
        if (!isId(profile.facultyId)) add("facultyId")
        if (!isText(profile.department, UserProfile.MAX_FIELD_LENGTH)) add("department")
        if (!isId(profile.departmentId)) add("departmentId")
        if (authPhoneNumber == null || profile.phoneNumber != authPhoneNumber ||
            !turkishE164.matches(profile.phoneNumber)
        ) {
            add("phoneNumber")
        }
        // gender and the *IsCustom flags are typed (enum / Boolean), so they always satisfy their rules.
    }

    /** `isString() && 1 <= length <= max && no [.$#[]/] && at least one non-space character`. */
    private fun isText(value: String, max: Int): Boolean =
        value.length in 1..max && !forbiddenCharacters.containsMatchIn(value) && value.any { it != ' ' }

    /** `isString() && 1 <= length <= 140 && matches ^[a-z0-9-]+$`. */
    private fun isId(value: String): Boolean = value.length <= MAX_ID_LENGTH && idPattern.matches(value)
}
