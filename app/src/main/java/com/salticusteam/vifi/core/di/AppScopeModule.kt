package com.salticusteam.vifi.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** A process-wide scope for work that must outlive any screen (session state, fire-and-forget reports). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {

    /** One failing child never cancels the others; Main.immediate so state updates reach the UI directly. */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The wall clock; tests pass a fixed or stepping clock instead. */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
