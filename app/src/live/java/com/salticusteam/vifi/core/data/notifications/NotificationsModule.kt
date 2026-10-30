package com.salticusteam.vifi.core.data.notifications

import com.salticusteam.vifi.core.data.firebase.FirestoreDeviceRegistrar
import com.salticusteam.vifi.core.data.firebase.FirebaseNotificationEventReporter
import com.salticusteam.vifi.core.data.firebase.FirestoreInboxReader
import com.salticusteam.vifi.core.data.firebase.FirestoreNotificationSettingsService
import com.salticusteam.vifi.core.data.notifications.admin.FirebaseNotificationAdminService
import com.salticusteam.vifi.core.data.preferences.DataStoreNotificationPromptHistory
import com.salticusteam.vifi.core.data.preferences.NotificationPromptHistory
import com.salticusteam.vifi.core.data.repository.DeviceRegistrar
import com.salticusteam.vifi.core.data.repository.DeviceRegistration
import com.salticusteam.vifi.core.data.repository.InboxReader
import com.salticusteam.vifi.core.data.repository.NotificationAdminService
import com.salticusteam.vifi.core.data.repository.NotificationEventReporter
import com.salticusteam.vifi.core.data.repository.NotificationSettingsService
import com.salticusteam.vifi.core.maps.GoogleLocationPicker
import com.salticusteam.vifi.core.notifications.DeviceRegistrationStore
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.LocationPicker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Notification bindings of the live flavor (4.1.0 targeted notifications): the inbox, device registration, campaign
 * consent and statistics on Firebase, the admin composer's callables, and the Google Maps location picker. The mock
 * flavor's module mirrors this one with fakes.
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
    abstract fun bindNotificationAdminService(impl: FirebaseNotificationAdminService): NotificationAdminService

    /** Starts the Maps / Places SDKs only when an admin first opens it, and only with a configured key. */
    @Binds
    @Singleton
    abstract fun bindLocationPicker(impl: GoogleLocationPicker): LocationPicker
}
