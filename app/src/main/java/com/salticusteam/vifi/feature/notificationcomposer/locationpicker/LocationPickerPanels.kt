package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

import androidx.annotation.PluralsRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.NotificationLocation

// The two panels of the location picker that sit over / under the map. They show the same state.

/** The place search field and, under it, what it found. Choosing a result moves the map to that place. */
@Composable
internal fun LocationPickerSearchPanel(
    state: LocationPickerUiState,
    onQueryChange: (String) -> Unit,
    onSuggestionSelected: (PlaceSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 6.dp,
    ) {
        Column {
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.location_picker_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.testTag("locationPicker.search.clear"),
                        ) {
                            Icon(
                                Icons.Filled.Cancel,
                                contentDescription = stringResource(R.string.location_picker_search_clear),
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("locationPicker.search"),
            )
            if (state.showsSearchPanel) {
                HorizontalDivider()
                SearchResults(
                    state = state,
                    onSuggestionSelected = { suggestion ->
                        focusManager.clearFocus()
                        onSuggestionSelected(suggestion)
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchResults(state: LocationPickerUiState, onSuggestionSelected: (PlaceSuggestion) -> Unit) {
    val resolveError = state.resolveError
    if (resolveError != null) {
        PanelNote(resolveError, Icons.Filled.Error, MaterialTheme.colorScheme.error)
        return
    }
    when (val search = state.searchState) {
        PlaceSearchState.Idle -> Unit
        PlaceSearchState.Searching -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.location_picker_searching),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PlaceSearchState.NoResults -> PanelNote(
            text = stringResource(R.string.location_picker_no_results),
            icon = Icons.Filled.Search,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is PlaceSearchState.Failed -> PanelNote(search.message, Icons.Filled.Error, MaterialTheme.colorScheme.error)
        is PlaceSearchState.Results -> LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
            items(search.suggestions, key = { it.id }) { suggestion ->
                SuggestionRow(suggestion = suggestion, onClick = { onSuggestionSelected(suggestion) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: PlaceSuggestion, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("locationPicker.suggestion.${suggestion.id}"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = suggestion.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        suggestion.subtitle?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PanelNote(text: String, icon: ImageVector, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

/** "… en fazla <limit> karakter olabilir." */
@Composable
private fun limitText(@PluralsRes id: Int, limit: Int): String = pluralStringResource(id, limit, limit)

/**
 * The pin's coordinates, the editable name (required) and address (optional), and "Bu konumu seç". A quiet note
 * says when the location permission is off.
 */
@Composable
internal fun LocationPickerDetailsCard(
    state: LocationPickerUiState,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val coordinatesDescription =
                stringResource(R.string.location_picker_coordinates_description, state.coordinateText)
            Text(
                text = state.coordinateText,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .semantics { contentDescription = coordinatesDescription }
                    .testTag("locationPicker.coordinates"),
            )

            if (state.isLocationDenied) {
                // A quiet note, not a prompt: the admin chose this, and the picker works without it.
                Row(
                    modifier = Modifier.testTag("locationPicker.locationDenied"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.LocationOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.location_picker_location_denied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.location_picker_name)) },
                isError = state.isNameTooLong,
                supportingText = if (state.isNameTooLong) {
                    { Text(limitText(R.plurals.location_picker_name_too_long, NotificationLocation.NAME_LIMIT)) }
                } else {
                    null
                },
                singleLine = true,
                // Place names are proper nouns that autocorrect would "fix".
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("locationPicker.name"),
            )

            val addressLoading = stringResource(R.string.location_picker_address_loading)
            OutlinedTextField(
                value = state.address,
                onValueChange = onAddressChange,
                label = { Text(stringResource(R.string.location_picker_address)) },
                trailingIcon = if (state.isLoadingAddress) {
                    {
                        // The address of a place tapped on the map is on its way.
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(18.dp)
                                .semantics { contentDescription = addressLoading }
                                .testTag("locationPicker.addressLoading"),
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    null
                },
                isError = state.isAddressTooLong,
                supportingText = if (state.isAddressTooLong) {
                    { Text(limitText(R.plurals.location_picker_address_too_long, NotificationLocation.ADDRESS_LIMIT)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("locationPicker.address"),
            )

            // Primary button content is onPrimary (black): white on the brand orange falls below 3:1.
            Button(
                onClick = onConfirm,
                enabled = state.canConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("locationPicker.confirm"),
            ) {
                Text(stringResource(R.string.location_picker_confirm), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
