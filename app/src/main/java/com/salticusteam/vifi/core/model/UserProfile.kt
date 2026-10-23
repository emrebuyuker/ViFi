package com.salticusteam.vifi.core.model

// Added in 2.5 — a signed-in user's profile, stored under `users/<uid>/profile`; the app gates on it.
/**
 * Pure value type, independent of Firebase. The phone number is not entered by the user; it mirrors the
 * verified number from the auth token. Each `*Id` is the canonical id (used for notification segments / FCM
 * topics) and `*IsCustom` is `true` when the user typed a value that was not on the bundled list.
 */
data class UserProfile(
    val name: String,
    val surname: String,
    val university: String,
    val universityId: String,
    val universityIsCustom: Boolean,
    val faculty: String,
    val facultyId: String,
    val facultyIsCustom: Boolean,
    val department: String,
    val departmentId: String,
    val departmentIsCustom: Boolean,
    val gender: Gender,
    /** The verified number the profile belongs to, in E.164 (`+905321234567`); set from the auth token. */
    val phoneNumber: String,
) {
    /** `Ad Soyad`. */
    val fullName: String get() = "$name $surname"

    companion object {
        /** Longest accepted name or surname. */
        const val MAX_NAME_LENGTH = 60

        /** Longest accepted university or department (matches the archive's node-name limit). */
        const val MAX_FIELD_LENGTH = 120
    }
}

// Added in 2.5 — how the user describes their gender; the raw value is the stored/analytics value.
/** The Turkish UI label is resolved in the UI layer from a string resource (not part of the model). */
enum class Gender(val raw: String) {
    FEMALE("female"),
    MALE("male");

    companion object {
        fun fromRaw(raw: String?): Gender? = entries.firstOrNull { it.raw == raw }
    }
}
