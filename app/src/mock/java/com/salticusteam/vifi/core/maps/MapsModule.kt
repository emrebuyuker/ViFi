package com.salticusteam.vifi.core.maps

import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.DeviceCoordinate
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.DeviceLocator
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.PlaceDetails
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.PlaceSearchError
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.PlaceSearcher
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.PlaceSuggestion
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.SearchArea
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The location picker's services in the mock flavor, which has no Google SDK and never reads the location. Only
 * there so the shared picker view model can be built; the mock picker offers a fixed place without a map or a
 * search, so neither is ever asked.
 */
@Module
@InstallIn(SingletonComponent::class)
object MapsModule {

    @Provides
    fun providePlaceSearcher(): PlaceSearcher = NoPlaceSearcher

    @Provides
    fun provideDeviceLocator(): DeviceLocator = NoDeviceLocator
}

/** Finds nothing: the mock flavor has no Places service. */
private object NoPlaceSearcher : PlaceSearcher {

    override suspend fun suggestions(query: String, area: SearchArea?): List<PlaceSuggestion> =
        throw PlaceSearchError.Unavailable

    override suspend fun place(suggestion: PlaceSuggestion): PlaceDetails = throw PlaceSearchError.Unavailable

    override suspend fun address(placeId: String): String? = null
}

/** Never has a fix: the mock flavor does not read the location. */
private object NoDeviceLocator : DeviceLocator {

    override suspend fun currentLocation(): DeviceCoordinate? = null
}
