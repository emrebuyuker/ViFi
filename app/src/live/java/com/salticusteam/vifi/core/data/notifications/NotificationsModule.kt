package com.salticusteam.vifi.core.data.notifications

import com.salticusteam.vifi.core.data.firebase.FirestoreDeviceRegistrar
import com.salticusteam.vifi.core.data.firebase.FirestoreInboxReader
import com.salticusteam.vifi.core.data.repository.DeviceRegistrar
import com.salticusteam.vifi.core.data.repository.DeviceRegistration
import com.salticusteam.vifi.core.data.repository.InboxReader
import com.salticusteam.vifi.core.data.repository.NotificationAdminService
import com.salticusteam.vifi.core.data.repository.NotificationEventReporter
import com.salticusteam.vifi.core.data.repository.NotificationSettingsService
import com.salticusteam.vifi.core.notifications.DeviceRegistrationStore
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.LocationPicker
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.UnavailableLocationPicker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Notification bindings of the live flavor (4.1.0 targeted notifications). Until each Firebase implementation lands,
 * a safe `Unavailable*` stand-in is bound and swapped for the real class here (device registration since PSH-04, the inbox
 * since INB-01; the rest in their items or NIT-01). The mock flavor's module mirrors this one with fakes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {

    @Binds
    @Singleton
    abstract fun bindInboxReader(impl: FirestoreInboxReader): InboxReader

    @Binds
    @Singleton
    abstract fun bindDeviceRegistrar(impl: FirestoreDeviceRegistrar): DeviceRegistrar

    @Binds
    @Singleton
    abstract fun bindDeviceRegistration(impl: DeviceRegistrationStore): DeviceRegistration

    @Binds
    @Singleton
    abstract fun bindNotificationSettingsService(impl: UnavailableNotificationSettingsService): NotificationSettingsService

    @Binds
    @Singleton
    abstract fun bindNotificationEventReporter(impl: UnavailableNotificationEventReporter): NotificationEventReporter

    @Binds
    @Singleton
    abstract fun bindNotificationAdminService(impl: UnavailableNotificationAdminService): NotificationAdminService

    companion object {
        /** The Google Maps picker replaces this with MAP-01…03. */
        @Provides
        @Singleton
        fun provideLocationPicker(): LocationPicker = UnavailableLocationPicker
    }
}
