package com.salticusteam.vifi.core.data.conversion

import com.salticusteam.vifi.core.data.moderation.FirebaseSubmitterProfileReader
import com.salticusteam.vifi.core.data.repository.ExamConversionAdminService
import com.salticusteam.vifi.core.data.repository.SubjectMappingService
import com.salticusteam.vifi.core.data.repository.SubmitterProfileReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Admin conversion, subject mapping and submitter bindings of the live flavor (4.2.0): the callable / Firestore
 * services of the AI conversion workflow (ADX-01…06), the original-pages URLs of a job and the RTDB submitter reader
 * (ADM-03).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ConversionModule {

    @Binds
    @Singleton
    abstract fun bindExamConversionAdminService(impl: FirebaseExamConversionAdminService): ExamConversionAdminService

    @Binds
    @Singleton
    abstract fun bindSubjectMappingService(impl: FirebaseSubjectMappingService): SubjectMappingService

    @Binds
    @Singleton
    abstract fun bindConversionFileUrls(impl: StorageConversionFileUrls): ConversionFileUrls

    /** ADM-03: the RTDB `users/<uid>/profile` reader (name and affiliation only). */
    @Binds
    @Singleton
    abstract fun bindSubmitterProfileReader(impl: FirebaseSubmitterProfileReader): SubmitterProfileReader
}
