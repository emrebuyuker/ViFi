package com.salticusteam.vifi.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.catalog.UniversityCatalog
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.CatalogItem
import com.salticusteam.vifi.core.model.CatalogSelection

/**
 * A searchable list to pick a value from, with a free-text fallback (iOS CatalogPickerView): the typed text can
 * be used as-is, so a university / faculty / department missing from the bundled list is still accepted.
 *
 * Shown over the form; choosing a value or going back returns to it. [testTagPrefix] gives the `.search`,
 * `.custom` and `.option.<id>` tags.
 */
@Composable
fun CatalogPicker(
    title: String,
    options: List<CatalogItem>,
    selection: CatalogSelection,
    testTagPrefix: String,
    onSelect: (CatalogSelection) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    allowsCustom: Boolean = true,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val trimmedQuery = query.trim()
    val matches = remember(options, query) { UniversityCatalog.filtered(options, query) }
    val showsCustomOption = allowsCustom && trimmedQuery.isNotEmpty() &&
        options.none { it.name.equals(trimmedQuery, ignoreCase = true) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ViFiTopAppBar(title = title, onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .testTag("$testTagPrefix.list"),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.profile_picker_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("$testTagPrefix.search"),
                )
            }

            if (showsCustomOption) {
                item(key = "custom") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.profile_picker_use_custom, trimmedQuery)) },
                        supportingContent = { Text(stringResource(R.string.profile_picker_custom_footer)) },
                        leadingContent = {
                            Icon(
                                Icons.Filled.AddCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier
                            .clickable { onSelect(CatalogSelection.custom(trimmedQuery)) }
                            .testTag("$testTagPrefix.custom"),
                    )
                    HorizontalDivider()
                }
            }

            items(matches, key = { it.id }) { item ->
                val isSelected = !selection.isCustom && item.id == selection.id
                ListItem(
                    headlineContent = { Text(item.name) },
                    trailingContent = if (isSelected) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null, // announced through the selected state
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .clickable { onSelect(CatalogSelection.catalog(item)) }
                        .semantics { selected = isSelected }
                        .testTag("$testTagPrefix.option.${item.id}"),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CatalogPickerPreview() {
    ViFiTheme {
        CatalogPicker(
            title = "Üniversite",
            options = listOf(
                CatalogItem(id = "bogazici-universitesi", name = "Boğaziçi Üniversitesi"),
                CatalogItem(id = "ege-universitesi", name = "Ege Üniversitesi"),
            ),
            selection = CatalogSelection.catalog(CatalogItem(id = "ege-universitesi", name = "Ege Üniversitesi")),
            testTagPrefix = "profile.university",
            onSelect = {},
            onBack = {},
        )
    }
}
