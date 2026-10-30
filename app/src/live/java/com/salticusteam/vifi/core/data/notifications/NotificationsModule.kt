package com.salticusteam.vifi.core.data.notifications

import com.salticusteam.vifi.core.data.firebase.FirestoreDeviceRegistrar
import com.salticusteam.vifi.core.data.firebase.FirebaseNotificationEventReporter
import com.salticusteam.vifi.core.data.firebase.FirestoreInboxReader
import com.salticusteam.vifi.core.data.firebase.FirestoreNotificationSettingsService
import com.salticusteam.vifi.core.data.preferences.DataStoreNotificationPromptHistory
import com.salticusteam.vifi.core.data.preferences.NotificationPromptHistory
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
 * Notification bindings of the live flavor (4.1.0 targeted notifications). Each Firebase implementation replaced its
 * safe `Unavailable*` stand-in here as it landed (device registration PSH-04, inbox INB-01, statistics INB-05, campaign
 * consent PRM-01); only the admin service still waits for ADM-01. The mock flavor's module mirrors this one with fakes.
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
    abstract fun bindNotificationSettingsService(impl: FirestoreNotificationSettingsService): NotificationSettingsService

    @Binds
    @Singleton
    abstract fun bindNotificationEventReporter(impl: FirebaseNotificationEventReporter): NotificationEventReporter

    @Binds
    @Singleton
    abstract fun bindNotificationPromptHistory(impl: DataStoreNotificationPromptHistory): NotificationPromptHistory

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
