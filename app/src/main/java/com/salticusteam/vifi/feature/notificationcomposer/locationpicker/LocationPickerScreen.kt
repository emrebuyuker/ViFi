package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.NotificationLocation
import java.util.UUID

/**
 * Draws the map under the picker's pin: a Google map in the live flavor, a plain surface in previews and tests. It
 * starts on `initialCamera`, makes each move in `cameraRequest` once (by its id), shows the device's location only
 * when `showsDeviceLocation`, fills its parent and reports what happens through `onEvent`.
 */
typealias LocationPickerMap = @Composable (
    initialCamera: MapCamera,
    cameraRequest: CameraRequest?,
    showsDeviceLocation: Boolean,
    onEvent: (LocationPickerMapEvent) -> Unit,
) -> Unit

/**
 * The admin's location picker (iOS `LocationPickerView`): [map] with a pin fixed at its center (the coordinate is
 * wherever the map is centered), a Places search over it, and under it the name and address the notification
 * shows. "Bu konumu seç" hands back a valid [NotificationLocation] to [onPick]; the close "✕" (and Back) calls
 * [onCancel]. It opens on the device's location, asking for the permission once (see [LocationPickerViewModel]).
 */
@Composable
fun LocationPickerScreen(
    initial: NotificationLocation?,
    onPick: (NotificationLocation) -> Unit,
    onCancel: () -> Unit,
    map: LocationPickerMap,
) {
    // One view model per presentation, so a reopened picker starts afresh; the saved key keeps it across rotation.
    val key = rememberSaveable { "locationPicker|${UUID.randomUUID()}" }
    val viewModel = hiltViewModel<LocationPickerViewModel, LocationPickerViewModel.Factory>(key = key) { factory ->
        factory.create(initial)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onLocationPermissionResult(granted = granted)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LocationPickerEvent.RequestLocationPermission -> permissionRequest.launch(LOCATION_PERMISSION)
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.locateDevice(context.locationAuthorization())
    }

    val cancel = {
        viewModel.onDismissed()
        onCancel()
    }
    BackHandler(onBack = cancel)

    LocationPickerContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSuggestionSelected = viewModel::onSuggestionSelected,
        onNameChange = viewModel::onNameChange,
        onAddressChange = viewModel::onAddressChange,
        onConfirm = {
            state.location?.let { location ->
                viewModel.onDismissed()
                onPick(location)
            }
        },
        onCancel = cancel,
        map = {
            map(state.initialCamera, state.cameraRequest, state.showsDeviceLocation, viewModel::onMapEvent)
        },
    )
}

/** Stateless picker: the bar, the map with its pin and the search over it, then the details card. */
@Composable
fun LocationPickerContent(
    state: LocationPickerUiState,
    onQueryChange: (String) -> Unit,
    onSuggestionSelected: (PlaceSuggestion) -> Unit,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    map: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LocationPickerTopBar(onClose = onCancel, closeDescription = stringResource(R.string.location_picker_cancel))
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val mapDescription = stringResource(R.string.location_picker_map_description)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // One element for TalkBack, like iOS: the map's own labels are not read one by one.
                        .clearAndSetSemantics {
                            contentDescription = mapDescription
                            testTag = "locationPicker.map"
                        },
                ) {
                    map()
                }
                CenterPin(modifier = Modifier.align(Alignment.Center))
                LocationPickerSearchPanel(
                    state = state,
                    onQueryChange = onQueryChange,
                    onSuggestionSelected = onSuggestionSelected,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp),
                )
            }
            LocationPickerDetailsCard(
                state = state,
                onNameChange = onNameChange,
                onAddressChange = onAddressChange,
                onConfirm = onConfirm,
            )
        }
    }
}

/** The pin fixed on the center of the map, its tip exactly on the chosen coordinate. */
@Composable
private fun CenterPin(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        // Where the tip lands, so the exact point stays visible under the pin.
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f), CircleShape),
        )
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                // The glyph's tip sits at 22/24 of its height: lift it so the tip, not the middle, is the center.
                .offset(y = -PIN_SIZE * 5 / 12)
                .size(PIN_SIZE),
        )
    }
}

private val PIN_SIZE = 36.dp

/**
 * Approximate only (product owner, 2026-10-30): the map opens on the admin's district, which needs no precise fix,
 * and Play asks apps that request FINE to justify it.
 */
private const val LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

/**
 * The location permission now: granted; refused before, which is when the system offers a rationale; otherwise never
 * asked. A permission refused for good also reads as never asked, and asking then returns the refusal without a
 * dialog, so the picker still asks the system at most once per opening.
 */
private fun Context.locationAuthorization(): LocationAuthorization {
    if (ContextCompat.checkSelfPermission(this, LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
        return LocationAuthorization.AUTHORIZED
    }
    val activity = findActivity() ?: return LocationAuthorization.NOT_DETERMINED
    val wasRefused = ActivityCompat.shouldShowRequestPermissionRationale(activity, LOCATION_PERMISSION)
    return if (wasRefused) LocationAuthorization.DENIED else LocationAuthorization.NOT_DETERMINED
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true)
@Composable
private fun LocationPickerContentPreview() {
    ViFiTheme {
        LocationPickerContent(
            state = LocationPickerUiState(
                initialCamera = LocationPickerViewModel.DEFAULT_CAMERA,
                latitude = 40.9903,
                longitude = 29.029,
                name = "Kadıköy",
                address = "Caferağa, Kadıköy/İstanbul",
                query = "Kadı",
                searchState = PlaceSearchState.Results(
                    listOf(PlaceSuggestion(id = "preview", title = "Kadıköy", subtitle = "İstanbul")),
                ),
                locationAuthorization = LocationAuthorization.DENIED,
            ),
            onQueryChange = {},
            onSuggestionSelected = {},
            onNameChange = {},
            onAddressChange = {},
            onConfirm = {},
            onCancel = {},
            map = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) },
        )
    }
}
