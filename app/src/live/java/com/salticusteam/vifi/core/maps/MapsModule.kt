package com.salticusteam.vifi.core.maps

import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.DeviceLocator
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.PlaceSearcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The location picker's Google services in the live flavor. Unscoped: each picker gets its own searcher (its own
 * Places session) and locator. Neither touches a Google SDK until the picker, past the key check, uses it.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MapsModule {

    @Binds
    abstract fun bindPlaceSearcher(impl: GooglePlacesSearcher): PlaceSearcher

    @Binds
    abstract fun bindDeviceLocator(impl: FusedDeviceLocator): DeviceLocator
}
