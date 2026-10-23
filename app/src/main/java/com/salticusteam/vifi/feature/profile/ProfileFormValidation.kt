package com.salticusteam.vifi.feature.profile

import com.salticusteam.vifi.core.model.CatalogSelection
import com.salticusteam.vifi.core.model.UserProfile
import com.salticusteam.vifi.core.util.Slug

/**
 * Turns the form fields into a storable profile (iOS ProfileFormViewModel.builtProfile). The database rules
 * enforce the full contract; this keeps the Save button honest and trims what the user typed.
 */
object ProfileFormValidation {

    /** Characters the Realtime Database rejects in a value. Control characters and line breaks are refused too. */
    private val forbiddenCharacters = Regex("[.$#\\[\\]/\\p{Cntrl}]")
    private val repeatedSpaces = Regex(" +")

    fun build(state: ProfileFormUiState): UserProfile? {
        val phoneNumber = state.phoneNumber ?: return null
        val gender = state.gender ?: return null
        val name = validField(state.name, UserProfile.MAX_NAME_LENGTH) ?: return null
        val surname = validField(state.surname, UserProfile.MAX_NAME_LENGTH) ?: return null
        val university = validSelection(state.university) ?: return null
        val faculty = validSelection(state.faculty) ?: return null
        val department = validSelection(state.department) ?: return null
        return UserProfile(
            name = name,
            surname = surname,
            university = university.name,
            universityId = university.id,
            universityIsCustom = university.isCustom,
            faculty = faculty.name,
            facultyId = faculty.id,
            facultyIsCustom = faculty.isCustom,
            department = department.name,
            departmentId = department.id,
            departmentIsCustom = department.isCustom,
            gender = gender,
            phoneNumber = phoneNumber,
        )
    }

    /**
     * The trimmed, space-collapsed value, or `null` when it is empty, longer than [max], or contains a character
     * the database forbids.
     */
    fun validField(input: String, max: Int): String? {
        val normalized = input.trim().replace(repeatedSpaces, " ")
        if (normalized.isEmpty() || normalized.length > max || forbiddenCharacters.containsMatchIn(normalized)) {
            return null
        }
        return normalized
    }

    /**
     * A selection with a valid display name and its canonical id (the catalog id, or a best-effort slug for a
     * custom value); `null` when the name is invalid or no id can be derived.
     */
    fun validSelection(selection: CatalogSelection): CatalogSelection? {
        val name = validField(selection.name, UserProfile.MAX_FIELD_LENGTH) ?: return null
        val id = if (selection.isCustom) Slug.make(name) else selection.id
        if (id.isEmpty()) return null
        return CatalogSelection(name = name, id = id, isCustom = selection.isCustom)
    }
}
