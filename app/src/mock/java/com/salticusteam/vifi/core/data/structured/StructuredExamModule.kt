package com.salticusteam.vifi.core.data.structured

import com.salticusteam.vifi.core.data.repository.ExamQuestionReader
import com.salticusteam.vifi.core.data.repository.fake.FakeExamQuestionReader
import com.salticusteam.vifi.core.data.repository.fake.FakeInboxReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Structured-exam bindings of the mock flavor (4.2.0): every exam id serves the 3 sample questions, and the sample
 * figure (`sample-page-1.jpg`) resolves to a URL in the sample bucket that the mock `SampleFilesInterceptor` answers.
 */
@Module
@InstallIn(SingletonComponent::class)
object StructuredExamModule {

    @Provides
    @Singleton
    fun provideExamQuestionReader(): ExamQuestionReader = FakeExamQuestionReader(latency = 200.milliseconds)

    @Provides
    @Singleton
    fun provideQuestionFigureUrlResolver(): QuestionFigureUrlResolver =
        StorageQuestionFigureUrlResolver(FakeInboxReader.MOCK_BUCKET)
}
