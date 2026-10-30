package com.salticusteam.vifi.core.data.notifications.admin

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The composer's image encoder is Firebase-free, so both flavors share this binding. (The admin service itself is
 * bound per flavor in `NotificationsModule`.)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationAdminModule {

    @Binds
    abstract fun bindNotificationImageEncoder(impl: ContentResolverNotificationImageEncoder): NotificationImageEncoder
}
