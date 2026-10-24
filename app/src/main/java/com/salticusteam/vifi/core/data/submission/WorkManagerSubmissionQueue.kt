package com.salticusteam.vifi.core.data.submission

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.salticusteam.vifi.core.model.ExamSubmissionError
import com.salticusteam.vifi.core.util.LogCategory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * [SubmissionQueue] on WorkManager: one unique job per draft, run by [SubmissionUploadWorker].
 *
 * - **Expedited** so it starts at once like the in-process upload did (Android 12+: an expedited job; below that
 *   WorkManager runs it in a short foreground service, see `SubmissionUploadWorker.getForegroundInfo`). Out of
 *   quota it degrades to regular work instead of failing.
 * - **Requires a connection**, so an offline submit waits for the network instead of failing.
 * - **Retries with exponential backoff** up to [SubmissionUploadWorker.MAX_ATTEMPTS]; the submit resumes, so a
 *   stopped or killed attempt (the expedited-job time limit included) loses only the file in flight.
 *
 * No `dataSync` foreground service is needed: Android 14+ would require its permission, a Play declaration and
 * (Android 15) a daily time cap, while the resumable per-file upload already survives the job being stopped.
 */
@Singleton
class WorkManagerSubmissionQueue @Inject constructor(
    private val workManager: WorkManager,
    private val store: SubmissionDraftStore,
    private val watchers: SubmissionWatchers,
) : SubmissionQueue {

    override suspend fun enqueue(draftId: String, pending: PendingSubmission) {
        try {
            store.writeManifest(draftId, pending)
        } catch (e: IOException) {
            LogCategory.FILES.log.w(e, "Could not queue a submission")
            throw ExamSubmissionError.Unknown(ExamSubmissionError.GENERIC_MESSAGE)
        }
        val request = OneTimeWorkRequestBuilder<SubmissionUploadWorker>()
            .setInputData(workDataOf(SubmissionUploadWorker.KEY_DRAFT_ID to draftId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag(submissionTag(pending.submissionId))
            .build()
        // KEEP: a second submit of a draft whose upload still runs must not start a competing upload.
        workManager.enqueueUniqueWork(uniqueName(draftId), ExistingWorkPolicy.KEEP, request).await()
    }

    override fun status(draftId: String, submissionId: String): Flow<SubmissionStatus> =
        workManager.getWorkInfosForUniqueWorkFlow(uniqueName(draftId))
            .map { infos -> status(infos.firstOrNull { submissionTag(submissionId) in it.tags }) }
            .distinctUntilChanged()
            .onStart { watchers.watch(draftId) }
            .onCompletion { watchers.unwatch(draftId) }

    internal companion object {
        const val TAG = "exam-submission"
        private const val BACKOFF_SECONDS = 30L

        fun uniqueName(draftId: String) = "exam-submission-$draftId"

        /** Tells this submit's job from an earlier, finished one of the same draft. */
        fun submissionTag(submissionId: String) = "exam-submission-id:$submissionId"

        fun status(info: WorkInfo?): SubmissionStatus = when (info?.state) {
            null -> SubmissionStatus.Unknown
            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> SubmissionStatus.Sending
            WorkInfo.State.SUCCEEDED -> SubmissionStatus.Sent
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> SubmissionStatus.Failed(
                ExamSubmissionError.fromAnalyticsReason(
                    info.outputData.getString(SubmissionUploadWorker.KEY_FAILURE_REASON),
                ),
            )
        }
    }
}
