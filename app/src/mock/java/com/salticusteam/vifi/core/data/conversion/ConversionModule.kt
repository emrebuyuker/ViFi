package com.salticusteam.vifi.core.data.conversion

import com.salticusteam.vifi.core.data.repository.ExamConversionAdminService
import com.salticusteam.vifi.core.data.repository.SubjectMappingService
import com.salticusteam.vifi.core.data.repository.SubmitterProfileReader
import com.salticusteam.vifi.core.data.repository.fake.FakeExamConversionAdminService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubjectMappingService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubmitterProfileReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/** Admin conversion, subject mapping and submitter bindings of the mock flavor (4.2.0): in-memory samples, no Firebase. */
@Module
@InstallIn(SingletonComponent::class)
object ConversionModule {

    @Provides
    @Singleton
    fun provideExamConversionAdminService(): ExamConversionAdminService =
        FakeExamConversionAdminService(latency = 300.milliseconds)

    @Provides
    @Singleton
    fun provideSubjectMappingService(): SubjectMappingService = FakeSubjectMappingService()

    @Provides
    @Singleton
    fun provideSubmitterProfileReader(): SubmitterProfileReader = FakeSubmitterProfileReader()
}
