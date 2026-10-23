package com.salticusteam.vifi.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.CatalogSelection
import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.PhoneNumber

/**
 * The editable profile fields, shared by the completion gate and the edit screen (iOS ProfileFormView): name,
 * education (opens the catalog pickers), gender and the read-only verified phone number.
 */
@Composable
fun ProfileFormFields(
    state: ProfileFormUiState,
    onNameChange: (String) -> Unit,
    onSurnameChange: (String) -> Unit,
    onPickerOpen: (CatalogField) -> Unit,
    onGenderSelected: (Gender) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !state.isWorking
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        FormSection(title = stringResource(R.string.profile_section_personal)) {
            NameField(
                value = state.name,
                label = stringResource(R.string.profile_name_label),
                onValueChange = onNameChange,
                enabled = enabled,
                testTag = "profile.name",
            )
            NameField(
                value = state.surname,
                label = stringResource(R.string.profile_surname_label),
                onValueChange = onSurnameChange,
                enabled = enabled,
                testTag = "profile.surname",
            )
        }

        FormSection(title = stringResource(R.string.profile_section_education)) {
            CatalogRow(CatalogField.UNIVERSITY, state.university, enabled, onPickerOpen)
            HorizontalDivider()
            CatalogRow(CatalogField.FACULTY, state.faculty, enabled, onPickerOpen)
            HorizontalDivider()
            CatalogRow(CatalogField.DEPARTMENT, state.department, enabled, onPickerOpen)
        }

        FormSection(title = stringResource(R.string.profile_section_gender)) {
            GenderSelector(selected = state.gender, enabled = enabled, onSelected = onGenderSelected)
        }

        FormSection(title = stringResource(R.string.profile_phone_label)) {
            Text(
                text = state.phoneNumber?.let { PhoneNumber.fromE164(it)?.formatted ?: it }
                    ?: stringResource(R.string.profile_phone_missing),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("profile.phone"),
            )
            Text(
                text = stringResource(R.string.profile_phone_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.error?.let { message ->
            ListItem(
                headlineContent = { Text(message, color = MaterialTheme.colorScheme.error) },
                leadingContent = {
                    Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.testTag("profile.error"),
            )
        }
    }
}

/** The picker title for [field]. */
@Composable
fun CatalogField.label(): String = stringResource(
    when (this) {
        CatalogField.UNIVERSITY -> R.string.profile_university_label
        CatalogField.FACULTY -> R.string.profile_faculty_label
        CatalogField.DEPARTMENT -> R.string.profile_department_label
    },
)

/** The Turkish label for [Gender] (the model carries only the stored value). */
@StringRes
fun Gender.labelResId(): Int = when (this) {
    Gender.FEMALE -> R.string.profile_gender_female
    Gender.MALE -> R.string.profile_gender_male
}

/** The stable test tag prefix for [field]'s row and picker (iOS accessibility identifiers). */
val CatalogField.testTag: String
    get() = when (this) {
        CatalogField.UNIVERSITY -> "profile.university"
        CatalogField.FACULTY -> "profile.faculty"
        CatalogField.DEPARTMENT -> "profile.department"
    }

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            // Secondary text like iOS section headers; the brand orange is too light for small text.
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun NameField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    testTag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    )
}

/** A row showing the current value; tapping it opens the catalog picker for [field]. */
@Composable
private fun CatalogRow(
    field: CatalogField,
    selection: CatalogSelection,
    enabled: Boolean,
    onPickerOpen: (CatalogField) -> Unit,
) {
    ListItem(
        headlineContent = { Text(field.label()) },
        supportingContent = {
            Text(
                text = selection.name.ifEmpty { stringResource(R.string.profile_select_placeholder) },
                color = if (selection.isEmpty) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onPickerOpen(field) }
            .testTag(field.testTag),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderSelector(selected: Gender?, enabled: Boolean, onSelected: (Gender) -> Unit) {
    val genders = Gender.entries
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile.gender"),
    ) {
        genders.forEachIndexed { index, gender ->
            SegmentedButton(
                selected = gender == selected,
                onClick = { onSelected(gender) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = genders.size),
                modifier = Modifier.testTag("profile.gender.${gender.raw}"),
            ) {
                Text(stringResource(gender.labelResId()))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileFormFieldsPreview() {
    ViFiTheme {
        ProfileFormFields(
            state = ProfileFormUiState(
                name = "Ayşe",
                university = CatalogSelection.stored("Boğaziçi Üniversitesi", "bogazici-universitesi", false),
                phoneNumber = "+905321234567",
            ),
            onNameChange = {},
            onSurnameChange = {},
            onPickerOpen = {},
            onGenderSelected = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
