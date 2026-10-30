package com.salticusteam.vifi.core.data.notifications

import com.salticusteam.vifi.core.data.preferences.NotificationPromptHistory
import com.salticusteam.vifi.core.data.preferences.fake.FakeNotificationPromptHistory
import com.salticusteam.vifi.core.data.repository.DeviceRegistrar
import com.salticusteam.vifi.core.data.repository.DeviceRegistration
import com.salticusteam.vifi.core.data.repository.InboxReader
import com.salticusteam.vifi.core.data.repository.NotificationAdminService
import com.salticusteam.vifi.core.data.repository.NotificationEventReporter
import com.salticusteam.vifi.core.data.repository.NotificationSettingsService
import com.salticusteam.vifi.core.data.repository.fake.FakeDeviceRegistrar
import com.salticusteam.vifi.core.data.repository.fake.FakeInboxReader
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationAdminService
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationEventReporter
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationSettingsService
import com.salticusteam.vifi.core.model.NotificationConsent
import com.salticusteam.vifi.core.notifications.DeviceRegistrationStore
import com.salticusteam.vifi.core.util.LaunchArg
import com.salticusteam.vifi.core.util.LaunchArgs
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.LocationPicker
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.UnavailableLocationPicker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.Instant
import javax.inject.Singleton

/**
 * Notification bindings of the mock flavor (iOS `AppEnvironment.mock()`): sample data only, nothing reaches
 * Firebase. Mirrors the live flavor's module; add a fake here whenever that one gains a binding.
 */
@Module
@InstallIn(SingletonComponent::class)
object NotificationsModule {

    /** The iOS sample inbox (`a1`, `p-paywall`, …), all unread; `vifi_open_notification <id>` highlights one. */
    @Provides
    @Singleton
    fun provideInboxReader(): InboxReader = FakeInboxReader()

    @Provides
    @Singleton
    fun provideDeviceRegistrar(): DeviceRegistrar = FakeDeviceRegistrar()

    /** The app's own store (its writes land in the fake registrar above). */
    @Provides
    @Singleton
    fun provideDeviceRegistration(store: DeviceRegistrationStore): DeviceRegistration = store

    /**
     * Never answered, so campaigns start off; `vifi_consent_reask` starts with a "no" (campaigns off), for the inbox
     * consent re-ask (iOS `-ViFiConsentReask`).
     */
    @Provides
    @Singleton
    fun provideNotificationSettingsService(): NotificationSettingsService = FakeNotificationSettingsService(
        initial = if (LaunchArgs.isSet(LaunchArg.CONSENT_REASK)) NotificationConsent(marketing = false) else null,
    )

    @Provides
    @Singleton
    fun provideNotificationEventReporter(): NotificationEventReporter = FakeNotificationEventReporter()

    /**
     * In memory and seeded per launch (iOS's mock suite is wiped at each launch), like iOS's launch arguments: a
     * fresh install with `vifi_notifications_prompt`; both steps already offered otherwise, plus a "no" 31 days ago
     * with `vifi_notifications_reask` (the permission, re-asked after the next submission) or `vifi_consent_reask`
     * (the consent, re-asked when the inbox opens).
     */
    @Provides
    @Singleton
    fun provideNotificationPromptHistory(): NotificationPromptHistory = FakeNotificationPromptHistory().apply {
        if (LaunchArgs.isSet(LaunchArg.NOTIFICATIONS_PROMPT)) return@apply
        primingOffered = true
        consentOffered = true
        val monthAgo = Instant.now().minus(Duration.ofDays(31))
        if (LaunchArgs.isSet(LaunchArg.NOTIFICATIONS_REASK)) permissionDeclinedAt = monthAgo
        if (LaunchArgs.isSet(LaunchArg.CONSENT_REASK)) consentDeclinedAt = monthAgo
    }

    /** Sample audience and history; sends stay on the device. Reached only with `vifi_admin`. */
    @Provides
    @Singleton
    fun provideNotificationAdminService(): NotificationAdminService = FakeNotificationAdminService()

    /** The fixed-place mock picker replaces this with MAP-04; the Google SDKs never start in the mock flavor. */
    @Provides
    @Singleton
    fun provideLocationPicker(): LocationPicker = UnavailableLocationPicker
}
