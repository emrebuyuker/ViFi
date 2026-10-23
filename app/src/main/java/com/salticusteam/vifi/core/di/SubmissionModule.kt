package com.salticusteam.vifi.core.di

import com.salticusteam.vifi.core.data.files.ContentResolverSubmissionFileSource
import com.salticusteam.vifi.core.data.repository.SubmissionFileSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Picked-file reading is Firebase-free, so both flavors share this binding (the uploads differ per flavor). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SubmissionModule {

    @Binds
    abstract fun bindSubmissionFileSource(impl: ContentResolverSubmissionFileSource): SubmissionFileSource
}
