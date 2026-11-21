package com.salticusteam.vifi.core.data.structured

import com.salticusteam.vifi.core.data.repository.ExamQuestionReader
import com.salticusteam.vifi.core.data.repository.fake.FakeExamQuestionReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/** Structured-exam bindings of the mock flavor (4.2.0): every exam id serves the 3 sample questions. */
@Module
@InstallIn(SingletonComponent::class)
object StructuredExamModule {

    @Provides
    @Singleton
    fun provideExamQuestionReader(): ExamQuestionReader = FakeExamQuestionReader(latency = 200.milliseconds)
}
