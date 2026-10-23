package com.salticusteam.vifi.feature.profile

import com.salticusteam.vifi.core.data.catalog.UniversityCatalog
import com.salticusteam.vifi.core.model.CatalogItem
import com.salticusteam.vifi.core.model.CatalogSelection
import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.UserProfile

/** Which catalog list a picker shows. */
enum class CatalogField { UNIVERSITY, FACULTY, DEPARTMENT }

/**
 * The profile form, shared by the completion gate and the edit screen (iOS ProfileFormViewModel's fields).
 * [picker] is the catalog list open over the form, `null` while the form itself is shown.
 */
data class ProfileFormUiState(
    val name: String = "",
    val surname: String = "",
    val university: CatalogSelection = CatalogSelection.EMPTY,
    val faculty: CatalogSelection = CatalogSelection.EMPTY,
    val department: CatalogSelection = CatalogSelection.EMPTY,
    val gender: Gender? = null,
    /** The verified number, shown read-only; `null` only for an account without one. */
    val phoneNumber: String? = null,
    val catalog: UniversityCatalog = UniversityCatalog.EMPTY,
    val picker: CatalogField? = null,
    val isWorking: Boolean = false,
    val error: String? = null,
) {
    /** The validated profile from the current fields, or `null` while something is missing or invalid. */
    val builtProfile: UserProfile? get() = ProfileFormValidation.build(this)

    val canSave: Boolean get() = !isWorking && builtProfile != null

    fun selection(field: CatalogField): CatalogSelection = when (field) {
        CatalogField.UNIVERSITY -> university
        CatalogField.FACULTY -> faculty
        CatalogField.DEPARTMENT -> department
    }

    fun options(field: CatalogField): List<CatalogItem> = when (field) {
        CatalogField.UNIVERSITY -> catalog.universities
        CatalogField.FACULTY -> catalog.faculties
        CatalogField.DEPARTMENT -> catalog.departments
    }

    companion object {
        /** The form prefilled for editing [existing], or empty for a first completion. */
        fun initial(phoneNumber: String?, existing: UserProfile?) = ProfileFormUiState(
            name = existing?.name.orEmpty(),
            surname = existing?.surname.orEmpty(),
            university = existing?.let {
                CatalogSelection.stored(it.university, it.universityId, it.universityIsCustom)
            } ?: CatalogSelection.EMPTY,
            faculty = existing?.let {
                CatalogSelection.stored(it.faculty, it.facultyId, it.facultyIsCustom)
            } ?: CatalogSelection.EMPTY,
            department = existing?.let {
                CatalogSelection.stored(it.department, it.departmentId, it.departmentIsCustom)
            } ?: CatalogSelection.EMPTY,
            gender = existing?.gender,
            phoneNumber = phoneNumber ?: existing?.phoneNumber,
        )
    }
}

/** One-shot results of the form. */
sealed interface ProfileFormEvent {
    /** The profile was stored; the edit screen closes (the gate opens on its own). */
    data object Saved : ProfileFormEvent
}
